(function () {
    window.Pages = window.Pages || {};

    window.Pages.learn = async function (params) {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        let heartbeatInterval = null;
        let player = null;

        try {
            const courseId = params.courseId;
            const lectureId = params.lectureId;

            const [course, lectures, resume] = await Promise.all([
                window.Api.getCourse(courseId),
                window.Api.getLectures(courseId),
                window.Api.getResume(lectureId)
            ]);

            let streamInfo = null;
            try {
                streamInfo = await window.Api.getStreamUrl(lectureId);
            } catch (streamErr) {
                console.warn("Stream URL 조회 실패:", streamErr.message);
            }

            const currentLecture = lectures.find(l => l.id == lectureId);
            if (!currentLecture) throw new Error("강의를 찾을 수 없습니다.");

            let html = `
            <div class="learn-layout">
                <div class="learn-main">
                    <div class="player-wrapper">
                        <video id="lms-player" class="video-js vjs-default-skin vjs-big-play-centered" playsinline></video>
                        <div id="player-msg" class="player-msg hidden"></div>
                    </div>
                    <div class="learn-info">
                        <h2>${Components.escapeHtml(currentLecture.title)}</h2>
                        <div class="learn-tabs">
                            <button class="tab-btn active" data-tab="desc">설명</button>
                            <button class="tab-btn" data-tab="resources">자료</button>
                        </div>
                        <div id="tab-desc" class="tab-content">
                            <p>${Components.escapeHtml(currentLecture.description || '설명이 없습니다.')}</p>
                        </div>
                        <div id="tab-resources" class="tab-content hidden">
                            <p>첨부 자료가 없습니다.</p>
                        </div>
                    </div>
                </div>
                <div class="learn-sidebar">
                    <div class="sidebar-header">
                        <h3>${Components.escapeHtml(course.title)}</h3>
                        <div id="sidebar-progress"></div>
                    </div>
                    <ol class="sidebar-curriculum">`;

            for (const lec of lectures) {
                const isCurrent = lec.id == lectureId;
                html += `
                    <li class="sidebar-item ${isCurrent ? 'current' : ''}">
                        <a href="#/courses/${courseId}/learn/${lec.id}" class="sidebar-link">
                            <span class="sidebar-check" id="check-${lec.id}"></span>
                            <span class="sidebar-title">${Components.escapeHtml(lec.title)}</span>
                            ${lec.durationSeconds ? `<span class="sidebar-duration">${Math.floor(lec.durationSeconds / 60)}분</span>` : ''}
                        </a>
                    </li>`;
            }

            html += `</ol></div></div>`;
            app.innerHTML = Components.page(user, html);

            // Tab switching
            document.querySelectorAll(".learn-tabs .tab-btn").forEach(btn => {
                btn.addEventListener("click", () => {
                    document.querySelectorAll(".learn-tabs .tab-btn").forEach(b => b.classList.remove("active"));
                    btn.classList.add("active");
                    document.querySelectorAll(".tab-content").forEach(tc => tc.classList.add("hidden"));
                    const target = document.getElementById("tab-" + btn.dataset.tab);
                    if (target) target.classList.remove("hidden");
                });
            });

            // Load progress for sidebar
            loadSidebarProgress(courseId, lectures);

            // Video source
            const videoSrc = streamInfo ? streamInfo.url : null;
            const isCompleted = resume.completed;
            let maxWatchedPosition = resume.lastPosition || 0;
            const MAX_SPEED = 2.0;

            if (!videoSrc) {
                const msgEl = document.getElementById("player-msg");
                if (msgEl) {
                    msgEl.textContent = "영상이 준비 중이거나 접근 권한이 없습니다.";
                    msgEl.classList.remove("hidden");
                }
                var playerEl = document.getElementById("lms-player");
                if (playerEl) playerEl.style.display = "none";
                return;
            }

            // Dispose any leftover Video.js instance with same ID
            try {
                var old = videojs.getPlayer("lms-player");
                if (old) { old.dispose(); }
            } catch (_) {}

            // Wait for DOM to fully settle after cleanup
            await new Promise(function (resolve) { setTimeout(resolve, 100); });

            // Verify element exists after delay
            if (!document.getElementById("lms-player")) return;

            // Initialize Video.js player
            player = videojs("lms-player", {
                controls: true,
                autoplay: false,
                preload: "auto",
                fluid: true,
                playbackRates: [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2.0],
                controlBar: {
                    children: [
                        "playToggle",
                        "currentTimeDisplay",
                        "timeDivider",
                        "durationDisplay",
                        "progressControl",
                        "playbackRateMenuButton",
                        "qualitySelector",
                        "volumePanel",
                        "fullscreenToggle"
                    ]
                },
                userActions: {
                    doubleClick: false
                }
            });

            // Set source
            if (streamInfo.type === "hls") {
                player.src({ src: videoSrc, type: "application/x-mpegURL" });

                // HLS 화질 선택 UI 활성화
                if (typeof player.hlsQualitySelector === "function") {
                    player.hlsQualitySelector({ displayCurrentQuality: true });
                }

                // Edge Auth 토큰이 있으면 모든 HLS 세그먼트 요청에 자동 첨부
                if (streamInfo.token) {
                    player.ready(function () {
                        var tech = player.tech({ IWillNotUseThisInPlugins: true });
                        if (tech && tech.vhs) {
                            tech.vhs.xhr.beforeRequest = function (options) {
                                if (options.uri && options.uri.includes('/hls/')) {
                                    var separator = options.uri.includes('?') ? '&' : '?';
                                    options.uri = options.uri + separator + 'token=' + streamInfo.token;
                                }
                                return options;
                            };
                        }
                    });
                }
            } else {
                player.src({ src: videoSrc, type: "video/mp4" });
            }

            // Disable right-click on player
            player.el().addEventListener("contextmenu", function (e) {
                e.preventDefault();
            });

            // Seek prevention for incomplete lectures
            if (!isCompleted) {
                var isSeeking = false;

                function enforceSeekLimit() {
                    if (isSeeking) return;
                    var current = player.currentTime();
                    if (current > maxWatchedPosition + 2) {
                        isSeeking = true;
                        player.currentTime(maxWatchedPosition);
                        showPlayerMsg("시청하지 않은 구간으로 이동할 수 없습니다.");
                        setTimeout(function () { isSeeking = false; }, 500);
                        return;
                    }
                    if (current > maxWatchedPosition) {
                        maxWatchedPosition = current;
                    }
                    updateWatchedBar(player, maxWatchedPosition);
                }

                player.on("seeking", enforceSeekLimit);
                player.on("seeked", enforceSeekLimit);
                player.on("timeupdate", enforceSeekLimit);
            }

            // Speed limit
            player.on("ratechange", function () {
                if (player.playbackRate() > MAX_SPEED) {
                    player.playbackRate(MAX_SPEED);
                    showPlayerMsg("최대 " + MAX_SPEED + "배속까지 가능합니다.");
                }
            });

            // Resume position
            player.on("loadedmetadata", function () {
                if (resume.lastPosition > 0 && !resume.completed) {
                    player.currentTime(resume.lastPosition);
                }
            });

            // Heartbeat every 30 seconds
            heartbeatInterval = setInterval(async () => {
                if (!player || player.paused() || player.ended()) return;
                try {
                    const result = await window.Api.heartbeat(lectureId, {
                        currentPosition: Math.floor(player.currentTime()),
                        watchedSeconds: 30,
                        playbackSpeed: player.playbackRate()
                    });
                    if (result.lectureJustCompleted) {
                        const checkEl = document.getElementById("check-" + lectureId);
                        if (checkEl) checkEl.textContent = "✓";
                        loadSidebarProgress(courseId, lectures);
                    }
                } catch (_) {}
            }, 30000);

            // On video ended
            player.on("ended", async function () {
                try {
                    await window.Api.completeLecture(lectureId);
                    const checkEl = document.getElementById("check-" + lectureId);
                    if (checkEl) checkEl.textContent = "✓";
                    loadSidebarProgress(courseId, lectures);

                    // Auto-navigate to next lecture
                    const idx = lectures.findIndex(l => l.id == lectureId);
                    if (idx >= 0 && idx < lectures.length - 1) {
                        const next = lectures[idx + 1];
                        window.Router.navigate("/courses/" + courseId + "/learn/" + next.id);
                    }
                } catch (_) {}
            });

        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }

        // Cleanup function
        return function () {
            if (heartbeatInterval) {
                clearInterval(heartbeatInterval);
                heartbeatInterval = null;
            }
            if (player) {
                try { player.dispose(); } catch (_) {}
                player = null;
            }
        };
    };

    function showPlayerMsg(text) {
        const msgEl = document.getElementById("player-msg");
        if (!msgEl) return;
        msgEl.textContent = text;
        msgEl.classList.remove("hidden");
        setTimeout(() => msgEl.classList.add("hidden"), 3000);
    }

    function updateWatchedBar(player, maxPos) {
        var duration = player.duration();
        if (!duration || duration <= 0) return;
        var bar = document.getElementById("watched-bar");
        if (!bar) {
            var progressHolder = player.el().querySelector(".vjs-progress-holder");
            if (!progressHolder) return;
            bar = document.createElement("div");
            bar.id = "watched-bar";
            bar.className = "vjs-watched-bar";
            progressHolder.appendChild(bar);
        }
        var pct = Math.min((maxPos / duration) * 100, 100);
        bar.style.width = pct + "%";
    }

    async function loadSidebarProgress(courseId, lectures) {
        try {
            const progress = await window.Api.getProgress(courseId);
            const el = document.getElementById("sidebar-progress");
            if (el) {
                el.innerHTML = Components.progressBar(progress.progressPercent);
            }

            for (const lec of lectures) {
                try {
                    const r = await window.Api.getResume(lec.id);
                    const checkEl = document.getElementById("check-" + lec.id);
                    if (checkEl && r.completed) {
                        checkEl.textContent = "✓";
                    }
                } catch (_) {}
            }
        } catch (_) {}
    }
})();
