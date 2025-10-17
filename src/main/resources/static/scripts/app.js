(function () {
    "use strict";

    const form = document.getElementById("hit-form");
    const xInput = document.getElementById("x-input");
    const ySelect = document.getElementById("y-select");
    const errorBox = document.getElementById("error-box");
    const resultBox = document.getElementById("result-box");
    const historyBody = document.querySelector("#history-table tbody");
    const pointsLayer = document.getElementById("plot-points");
    const themeToggle = document.getElementById("theme-toggle");
    const bodyEl = document.body;
    const allowedR = new Set(["1", "1.5", "2", "2.5", "3"]);
    const HISTORY_STORAGE_KEY = "lab1-history-cache";
    const themeStorageKey = "lab1-theme";
    const prefersReducedMotion = window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)") : { matches: false };
    let themeAnimationReady = false;

    const BASE_POINT_RADIUS = 3;
    const LATEST_POINT_RADIUS = 4.8;


    initTheme();
    initRipples();
    restoreHistoryFromStorage();
    scheduleAppMounted();

    xInput.addEventListener("input", () => {
        const sanitized = xInput.value.replace(/[^0-9.,\-]/g, "");

        if (sanitized !== xInput.value) {
            xInput.value = sanitized;
        }
    });

    form.addEventListener("submit", async (event) => {

        event.preventDefault();
        errorBox.textContent = "";

        const validation = validateForm();
        if (!validation.valid) {
            errorBox.textContent = validation.message;
            return;
        }

        const submitButton = form.querySelector("button[type=submit]");
        submitButton.disabled = true;
        try {
            const response = await fetch(form.action, {
                method: "POST",

                headers: {
                    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                    "Accept": "application/json"
                },
                body: new URLSearchParams({

                    x: validation.data.x,
                    y: validation.data.y,
                    r: validation.data.r
                }).toString()
            });

            if (!response.ok) {

                throw new Error(`Сервер вернул статус ${response.status}`);
            }


            const payload = await response.json();
            handleResponse(payload);
        } catch (err) {
            console.error(err);
            errorBox.textContent = "Не удалось получить ответ от сервера. Проверьте подключение и попробуйте снова.";
        } finally {
            submitButton.disabled = false;
        }
    });

    if (themeToggle) {
        themeToggle.addEventListener("click", () => {
            const nextTheme = bodyEl.classList.contains("theme-dark") ? "light" : "dark";
            applyTheme(nextTheme);
            try {
                localStorage.setItem(themeStorageKey, nextTheme);
            } catch (storageError) {
                console.warn("Не удалось сохранить тему", storageError);
            }
        });
    }


    function scheduleAppMounted() {
        if (bodyEl.classList.contains("app-mounted")) {
            return;
        }
        const delay = prefersReducedMotion.matches ? 0 : 560;

        window.setTimeout(() => bodyEl.classList.add("app-mounted"), delay);
    }

    function initTheme() {

        const storedTheme = (() => {
            try {
                return localStorage.getItem(themeStorageKey);

            } catch (err) {
                return null;
            }
        })();
        const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;

        const initialTheme = storedTheme === "dark" || storedTheme === "light" ? storedTheme : (prefersDark ? "dark" : "light");

        applyTheme(initialTheme);

    }

    function applyTheme(theme) {
        bodyEl.classList.toggle("theme-dark", theme === "dark");
        bodyEl.classList.toggle("theme-light", theme !== "dark");
        if (themeToggle) {
            themeToggle.setAttribute("aria-pressed", theme === "dark" ? "true" : "false");
        }
        if (!prefersReducedMotion.matches && themeAnimationReady) {
            triggerThemeTransition();

        }
        themeAnimationReady = true;
    }

    function initRipples() {
        if (prefersReducedMotion.matches) {
            return;
        }
        const rippleTargets = document.querySelectorAll(".action-button, .theme-toggle");
        rippleTargets.forEach((target) => {
            target.addEventListener("click", createRipple);
        });
    }


    function createRipple(event) {
        const host = event.currentTarget;
        const rect = host.getBoundingClientRect();
        const ripple = document.createElement("span");
        ripple.className = "ripple";
        const size = Math.max(rect.width, rect.height);

        ripple.style.width = ripple.style.height = `${size}px`;
        ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
        ripple.style.top = `${event.clientY - rect.top - size / 2}px`;
        host.appendChild(ripple);

        ripple.addEventListener("animationend", () => ripple.remove());
    }

    function triggerThemeTransition() {
        bodyEl.classList.remove("theme-transition");
        void bodyEl.offsetWidth;
        bodyEl.classList.add("theme-transition");
        setTimeout(() => bodyEl.classList.remove("theme-transition"), 520);
    }

    function restoreHistoryFromStorage() {
        try {
            const raw = localStorage.getItem(HISTORY_STORAGE_KEY);
            if (!raw) {

                return;
            }
            const stored = JSON.parse(raw);
            if (Array.isArray(stored) && stored.length > 0) {
                renderHistory(stored, { animateNew: false });

            }
        } catch (err) {
            console.warn('Не удалось восстановить историю из localStorage', err);
        }
    }

    function validateForm() {

        const errors = [];
        const rawX = xInput.value.trim().replace(",", ".");
        const rawY = ySelect.value;
        const selectedR = form.querySelector("input[name='r']:checked");

        if (!rawX || !/^[-+]?\d+(\.\d+)?$/.test(rawX)) {
            errors.push("Введите числовое значение X.");
        } else if (Number(rawX) < -3 || Number(rawX) > 5) {
            errors.push("X должен находиться в диапазоне (-3 ... 5).");
        }

        const y = Number(rawY);
        if (rawY.length === 0 || Number.isNaN(y)) {
            errors.push("Выберите допустимое значение Y.");
        }

        if (!selectedR) {
            errors.push("Выберите значение R.");
        }

        let r = null;
        if (selectedR) {
            r = selectedR.value;
            if (!allowedR.has(r)) {
                errors.push("Выбранное значение R недопустимо.");
            }
        }

        if (errors.length > 0) {
            return { valid: false, message: errors.join(" "), data: null };
        }

        return {
            valid: true,
            message: "",

            data: {

                x: rawX,
                y: rawY,
                r
            }
        };
    }

    function handleResponse(payload) {

        if (!payload || typeof payload !== "object") {
            errorBox.textContent = 'Ответ сервера имеет неизвестный формат.';
            return;
        }

        if (payload.status === "error") {
            if (Array.isArray(payload.errors) && payload.errors.length > 0) {
                errorBox.textContent = payload.errors.join(" \u2022 ");
            } else {
                errorBox.textContent = payload.message || "Сервер сообщил об ошибке.";
            }
            if (Array.isArray(payload.history)) {
                renderHistory(payload.history, { animateNew: false });
                persistHistory(payload.history);
            }

            toggleResultBox(false);
            return;

        }


        if (payload.status === "ok" && payload.data) {

            toggleResultBox(true);
            const { x, y, r, hit, currentTime, processingTimeMs } = payload.data;
            const hitText = hit ? 'точка попадает' : 'точка не попадает';
            resultBox.innerHTML = `Точка (<strong>${formatNumber(x)}</strong>, <strong>${formatNumber(y)}</strong>) при R = <strong>${formatNumber(r)}</strong> — ${hitText}.` +
                `<br>Время ответа: <strong>${escapeHtml(formatTimestamp(currentTime))}</strong>, обработка: <strong>${formatNumber(processingTimeMs)}</strong> мс.`;
        }

        const local = (() => {
            try {
                const raw = localStorage.getItem(HISTORY_STORAGE_KEY);
                return raw ? JSON.parse(raw) : [];
            } catch { return []; }
        })();
        const incoming = Array.isArray(payload.history) ? payload.history : [];
        const mergedMap = new Map();
        const put = (rec) => {
            if (!rec) return;
            mergedMap.set(buildHistoryKey(rec), rec);
        };
        local.forEach(put);
        incoming.forEach(put);
        if (payload.status === "ok" && payload.data) put(payload.data);
        const merged = Array.from(mergedMap.values());

        persistHistory(merged);
        renderHistory(merged, { animateNew: true });
    }


    function renderHistory(entries, options = {}) {
        const { animateNew = false } = options;
        const ordered = Array.isArray(entries) ? [...entries].reverse() : [];
        const existingRows = new Map();
        historyBody.querySelectorAll('tr').forEach((row) => {
            const key = row.dataset.key;

            if (key) {
                existingRows.set(key, row);
            }
        });
        const fragment = document.createDocumentFragment();

        let animationIndex = 0;

        ordered.forEach((record) => {
            if (!record) {
                return;
            }

            const key = buildHistoryKey(record);

            let row = existingRows.get(key);
            if (row) {
                existingRows.delete(key);
                row.classList.remove('history-row');
                row.style.removeProperty('--row-delay');
            } else {
                row = document.createElement('tr');
                row.dataset.key = key;

                if (animateNew) {

                    row.classList.add('history-row');
                    row.style.setProperty('--row-delay', `${Math.min(animationIndex, 6) * 60}ms`);
                    animationIndex += 1;

                }
            }
            row.dataset.key = key;
            row.innerHTML = [
                formatNumber(record.x),
                formatNumber(record.y),
                formatNumber(record.r),
                record.hit ? 'Да' : 'Нет',
                escapeHtml(formatTimestamp(record.currentTime)),
                formatNumber(record.processingTimeMs)
            ].map((cell) => `<td>${cell}</td>`).join('');
            fragment.appendChild(row);
        });

        historyBody.replaceChildren(fragment);
        updatePlotPoints(ordered);
    }

    function buildHistoryKey(record) {
        return [record.x, record.y, record.r, record.currentTime, record.processingTimeMs]
            .map((value) => `${value ?? ''}`)
            .join('|');
    }

    function updatePlotPoints(entries) {
        if (!pointsLayer) {
            return;

        }
        if (!Array.isArray(entries) || entries.length === 0) {

            pointsLayer.replaceChildren();
            return;
        }
        const fragment = document.createDocumentFragment();
        for (let index = entries.length - 1; index >= 0; index -= 1) {
            appendPlotPoint(fragment, entries[index], index === 0);
        }
        pointsLayer.replaceChildren(fragment);
    }

    function appendPlotPoint(fragment, record, isLatest) {
        if (!record) {
            return;
        }
        const x = Number(record.x);
        const y = Number(record.y);
        const r = Number(record.r);
        if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(r) || r === 0) {
            return;
        }
        const scale = 120 / r;
        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', String(x * scale));
        circle.setAttribute('cy', String(-y * scale));
        circle.setAttribute('r', isLatest ? LATEST_POINT_RADIUS.toString() : BASE_POINT_RADIUS.toString());
        circle.classList.add('plot-point');
        circle.classList.add(record.hit ? 'plot-point--hit' : 'plot-point--miss');
        if (isLatest) {
            circle.classList.add('plot-point--latest');
        }
        circle.setAttribute('data-x', record.x);
        circle.setAttribute('data-y', record.y);
        circle.setAttribute('data-r', record.r);
        fragment.appendChild(circle);
    }

    function persistHistory(entries) {
        try {
            if (!Array.isArray(entries)) {

                localStorage.removeItem(HISTORY_STORAGE_KEY);
                return;
            }
            localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(entries));
        } catch (err) {
            console.warn('Не удалось сохранить историю в localStorage', err);
        }
    }

    function toggleResultBox(visible) {
        resultBox.hidden = !visible;
    }

    function formatNumber(value) {
        if (value === null || value === undefined) {
            return "—";
        }
        if (typeof value === 'string') {
            return escapeHtml(value);
        }
        const number = Number(value);
        if (Number.isNaN(number)) {
            return escapeHtml(String(value));
        }
        return number.toString();
    }

    function formatTimestamp(value) {
        if (!value) {
            return '-';
        }
        let normalized = typeof value === 'string' ? value.trim() : value;
        if (typeof normalized === 'string') {

            normalized = normalized.replace(/(\.\d{3})\d*(?=(Z|[+-]\d{2}:\d{2})?$)/, '$1');
        }
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }
        const pad2 = (num) => String(Math.trunc(num)).padStart(2, '0');
        const year = date.getFullYear();
        const month = pad2(date.getMonth() + 1);
        const day = pad2(date.getDate());
        const hours = pad2(date.getHours());
        const minutes = pad2(date.getMinutes());
        const seconds = pad2(date.getSeconds());
        return `${day}.${month}.${year} ${hours}:${minutes}:${seconds}`;
    }

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, "&amp;")

            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");

    }
})();
