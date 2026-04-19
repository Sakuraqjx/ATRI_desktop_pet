(function () {
    const shell = document.getElementById("pet-shell");
    const canvas = document.getElementById("live2d-canvas");
    const status = document.getElementById("pet-status");
    const loadingLayer = document.getElementById("loading-layer");

    const PIXI_URL = "https://cdn.jsdelivr.net/npm/pixi.js@6.5.10/dist/browser/pixi.min.js";
    const CUBISM_CORE_URL = "https://cubism.live2d.com/sdk-web/cubismcore/live2dcubismcore.min.js";
    const PIXI_LIVE2D_URL = "https://cdn.jsdelivr.net/npm/pixi-live2d-display@0.4.0/dist/cubism4.min.js";

    const state = {
        messageRevision: -1,
        expressionRevision: -1,
        outfitRevision: -1,
        motionRevision: -1
    };

    let app = null;
    let model = null;
    let modelConfig = null;
    let singleTapTimer = null;
    let dragMoved = false;
    let dragging = false;
    let pointerStart = null;
    let statusHideTimer = null;
    let expressionFileMap = null;
    let outfitParameterCache = new Map();
    let currentOutfitExpression = null;
    let currentOutfitParameters = null;

    function backendUrl(path) {
        return `${window.location.origin}${path}`;
    }

    async function post(path, body) {
        await fetch(backendUrl(path), {
            method: "POST",
            headers: {
                "Content-Type": "text/plain; charset=utf-8"
            },
            body: body ?? ""
        });
    }

    async function postInteraction(type, payload) {
        await fetch(
            backendUrl(`/api/interaction?type=${encodeURIComponent(type)}&payload=${encodeURIComponent(payload ?? "")}`),
            { method: "POST" }
        );
    }

    async function fetchJson(path) {
        const response = await fetch(backendUrl(path), { cache: "no-store" });
        if (!response.ok) {
            throw new Error(`${path} failed: ${response.status}`);
        }
        return response.json();
    }

    function report(level, message) {
        post(`/api/log?level=${encodeURIComponent(level)}`, message).catch(() => {
            console[level === "error" ? "error" : "log"](message);
        });
    }

    function setStatus(text, autoHide = true) {
        if (!text) {
            status.classList.add("is-hidden");
            return;
        }

        status.textContent = text;
        status.classList.remove("is-hidden");

        if (statusHideTimer) {
            window.clearTimeout(statusHideTimer);
            statusHideTimer = null;
        }

        if (autoHide) {
            statusHideTimer = window.setTimeout(() => {
                status.classList.add("is-hidden");
                statusHideTimer = null;
            }, 2600);
        }
    }

    function loadScript(src) {
        return new Promise((resolve, reject) => {
            const existing = document.querySelector(`script[data-src="${src}"]`);
            if (existing) {
                if (existing.dataset.loaded === "true") {
                    resolve();
                    return;
                }
                existing.addEventListener("load", () => resolve(), { once: true });
                existing.addEventListener("error", () => reject(new Error(`Failed to load ${src}`)), { once: true });
                return;
            }

            const script = document.createElement("script");
            script.src = src;
            script.async = false;
            script.dataset.src = src;
            script.onload = () => {
                script.dataset.loaded = "true";
                resolve();
            };
            script.onerror = () => reject(new Error(`Failed to load ${src}`));
            document.head.appendChild(script);
        });
    }

    async function ensureRuntime() {
        await loadScript(PIXI_URL);
        await loadScript(CUBISM_CORE_URL);
        await loadScript(PIXI_LIVE2D_URL);

        if (!window.PIXI || !window.PIXI.live2d || !window.PIXI.live2d.Live2DModel) {
            throw new Error("PIXI Live2D runtime is unavailable.");
        }
    }

    function ensurePixiApp() {
        if (app) {
            return;
        }

        app = new window.PIXI.Application({
            view: canvas,
            autoStart: true,
            backgroundAlpha: 0,
            transparent: true,
            antialias: true,
            resizeTo: shell
        });

        app.ticker.add(() => {
            enforceOutfitParameters();
        });

        const renderer = app.renderer;
        const gl = renderer.gl;
        const attrs = gl && gl.getContextAttributes ? gl.getContextAttributes() : null;
        report("info", `renderer=${renderer.type} attrs=${JSON.stringify(attrs)}`);
    }

    async function loadModel() {
        if (!modelConfig) {
            throw new Error("No model configuration available.");
        }

        ensurePixiApp();

        if (model) {
            app.stage.removeChild(model);
            model.destroy();
            model = null;
        }

        expressionFileMap = null;
        outfitParameterCache = new Map();
        currentOutfitExpression = null;
        currentOutfitParameters = null;

        const Live2DModel = window.PIXI.live2d.Live2DModel;
        model = await Live2DModel.from(`./${modelConfig.entry}`, { autoInteract: false });
        model.anchor.set(0.5, 0.5);
        app.stage.addChild(model);
        layoutModel();

        loadingLayer.classList.add("is-hidden");
        setStatus(`${modelConfig.name} 已就位。`);
        report("info", `model loaded: ${modelConfig.id}`);
    }

    function layoutModel() {
        if (!model) {
            return;
        }

        const bounds = model.getLocalBounds();
        const shellWidth = Math.max(shell.clientWidth, 1);
        const shellHeight = Math.max(shell.clientHeight, 1);
        const targetWidth = shellWidth * 0.82;
        const targetHeight = shellHeight * 0.92;
        const scale = Math.min(
            targetWidth / Math.max(bounds.width, 1),
            targetHeight / Math.max(bounds.height, 1)
        );

        model.scale.set(scale);
        model.pivot.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
        model.x = shellWidth / 2;
        model.y = shellHeight / 2;
        report(
            "info",
            `layout shell=${shellWidth}x${shellHeight} bounds=${bounds.x},${bounds.y},${bounds.width},${bounds.height} scale=${scale.toFixed(4)}`
        );
    }

    async function ensureExpressionFileMap() {
        if (expressionFileMap) {
            return expressionFileMap;
        }

        const modelJson = await fetchJson(`/${modelConfig.entry}`);
        const expressions = modelJson?.FileReferences?.Expressions || [];
        expressionFileMap = new Map();
        for (const expression of expressions) {
            if (expression?.Name && expression?.File) {
                expressionFileMap.set(expression.Name, expression.File);
            }
        }
        return expressionFileMap;
    }

    function modelBaseDir() {
        const lastSlash = modelConfig.entry.lastIndexOf("/");
        return lastSlash >= 0 ? modelConfig.entry.slice(0, lastSlash + 1) : "";
    }

    function normalizeOutfitParameters(parameters) {
        if (!Array.isArray(parameters) || parameters.length === 0) {
            return null;
        }

        return parameters
            .filter((parameter) => parameter && typeof parameter.id === "string")
            .map((parameter) => ({
                id: parameter.id,
                value: Number(parameter.value ?? 0),
                blend: String(parameter.blend ?? "Overwrite")
            }));
    }

    async function loadOutfitParameters(expressionName) {
        if (!expressionName) {
            return null;
        }
        if (outfitParameterCache.has(expressionName)) {
            return outfitParameterCache.get(expressionName);
        }

        const fileMap = await ensureExpressionFileMap();
        const relativeFile = fileMap.get(expressionName);
        if (!relativeFile) {
            report("warn", `outfit expression not found: ${expressionName}`);
            outfitParameterCache.set(expressionName, null);
            return null;
        }

        const payload = await fetchJson(`/${modelBaseDir()}${relativeFile}`);
        const parameters = Array.isArray(payload?.Parameters)
            ? payload.Parameters
                .filter((parameter) => parameter && typeof parameter.Id === "string")
                .map((parameter) => ({
                    id: parameter.Id,
                    value: Number(parameter.Value ?? 0),
                    blend: String(parameter.Blend ?? "Overwrite")
                }))
            : [];

        outfitParameterCache.set(expressionName, parameters);
        return parameters;
    }

    async function applyOutfit(expressionName, inlineParameters) {
        currentOutfitExpression = expressionName || null;
        currentOutfitParameters = normalizeOutfitParameters(inlineParameters);
        if (!currentOutfitParameters && currentOutfitExpression) {
            currentOutfitParameters = await loadOutfitParameters(currentOutfitExpression);
        }
        enforceOutfitParameters();
    }

    function enforceOutfitParameters() {
        if (!model || !currentOutfitParameters || currentOutfitParameters.length === 0) {
            return;
        }

        const coreModel = model?.internalModel?.coreModel;
        if (!coreModel) {
            return;
        }

        for (const parameter of currentOutfitParameters) {
            if (parameter.blend === "Add" && typeof coreModel.addParameterValueById === "function") {
                coreModel.addParameterValueById(parameter.id, parameter.value);
                continue;
            }
            if (typeof coreModel.setParameterValueById === "function") {
                coreModel.setParameterValueById(parameter.id, parameter.value);
            }
        }
    }

    async function applyExpression(kind, value) {
        if (!model || typeof model.expression !== "function") {
            return;
        }

        const aliases = modelConfig?.expressionAliases || {};
        const expressionName = kind === "named" ? value : (aliases[value] || value);

        try {
            await model.expression(expressionName);
        } catch (error) {
            report("error", `expression failed: ${error.stack || error.message}`);
        }
    }

    async function applyMotion(group, name) {
        if (!group || !name) {
            return;
        }
        if (!model) {
            return;
        }

        const motionBindings = modelConfig?.motionBindings || {};
        const mapped = motionBindings[`${group}:${name}`];
        if (!mapped) {
            return;
        }

        try {
            await model.motion(mapped.group, mapped.index);
        } catch (error) {
            report("error", `motion failed: ${error.stack || error.message}`);
        }
    }

    async function syncState() {
        const snapshot = await fetchJson("/api/state");

        if (snapshot.messageRevision !== state.messageRevision) {
            state.messageRevision = snapshot.messageRevision;
            setStatus(snapshot.message);
        }

        if (snapshot.outfitRevision !== state.outfitRevision) {
            state.outfitRevision = snapshot.outfitRevision;
            await applyOutfit(snapshot.outfitExpression, snapshot.outfitParameters);
        }

        if (snapshot.expressionRevision !== state.expressionRevision) {
            state.expressionRevision = snapshot.expressionRevision;
            await applyExpression(snapshot.expressionKind, snapshot.expressionValue);
        }

        if (snapshot.motionRevision !== state.motionRevision) {
            state.motionRevision = snapshot.motionRevision;
            await applyMotion(snapshot.motionGroup, snapshot.motionName);
        }
    }

    function beginDrag(screenX, screenY) {
        dragging = true;
        dragMoved = false;
        pointerStart = { x: screenX, y: screenY };
        window.desktopShell?.startDrag(screenX, screenY);
    }

    function updateDrag(screenX, screenY) {
        if (!dragging) {
            return;
        }

        const distance = pointerStart
            ? Math.hypot(screenX - pointerStart.x, screenY - pointerStart.y)
            : 0;
        if (distance > 4) {
            dragMoved = true;
        }
        window.desktopShell?.dragMove(screenX, screenY);
    }

    function endDrag() {
        dragging = false;
        window.desktopShell?.endDrag();
        window.setTimeout(() => {
            dragMoved = false;
            pointerStart = null;
        }, 0);
    }

    function attachInteractions() {
        shell.addEventListener("pointermove", (event) => {
            if (model) {
                model.focus(event.clientX, event.clientY);
            }
            if ((event.buttons & 1) === 1) {
                updateDrag(event.screenX, event.screenY);
            }
        });

        shell.addEventListener("pointerdown", (event) => {
            if (event.button === 0) {
                beginDrag(event.screenX, event.screenY);
            }
        });

        window.addEventListener("pointerup", endDrag);

        shell.addEventListener("click", () => {
            if (dragMoved) {
                return;
            }
            if (singleTapTimer) {
                window.clearTimeout(singleTapTimer);
            }
            singleTapTimer = window.setTimeout(() => {
                postInteraction("pet.tap", "body").catch((error) => report("error", error.message));
                singleTapTimer = null;
            }, 180);
        });

        shell.addEventListener("dblclick", () => {
            if (dragMoved) {
                return;
            }
            if (singleTapTimer) {
                window.clearTimeout(singleTapTimer);
                singleTapTimer = null;
            }
            postInteraction("pet.doubleTap", "body").catch((error) => report("error", error.message));
        });

        shell.addEventListener("contextmenu", (event) => {
            event.preventDefault();
            window.desktopShell?.showContextMenu();
        });

        window.addEventListener("resize", layoutModel);
        window.addEventListener("error", (event) => {
            report("error", `window error: ${event.message} @ ${event.filename}:${event.lineno}`);
        });
        window.addEventListener("unhandledrejection", (event) => {
            const reason = event.reason && (event.reason.stack || event.reason.message || String(event.reason));
            report("error", `unhandled rejection: ${reason}`);
        });
    }

    (async function init() {
        try {
            attachInteractions();
            setStatus("正在读取模型配置...", false);
            modelConfig = await fetchJson("/api/model");
            await ensureRuntime();
            await loadModel();
            await syncState();
            window.setInterval(() => {
                syncState().catch((error) => report("error", error.message));
            }, 250);
        } catch (error) {
            report("error", error.stack || error.message);
            setStatus("Live2D 加载失败。", false);
        }
    })();
})();
