(function () {
    window.Pages = window.Pages || {};

    window.Pages.learn = async function (params) {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        let heartbeatInterval = null;

        try {
            const courseId = params.courseId;
            const lectureId = params.lectureId;

            const [course, lectures, resume] = await Promise.all([
                window.Api.getCourse(courseId),
                window.Api.getLectures(courseId),
                window.Api.getResume(lectureId)
            ]);

            const currentLecture = lectures.find(l => l.id == lectureId);
            if (!currentLecture) throw new Error("강의를 찾을 수 없습니다.");

            let html = `
            <div class="learn-layout">
                <div class="learn-main">
                    <div class="player-wrapper">
                        <video id="hls-player" controls playsinline></video>
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

            // Setup HLS player
            const video = document.getElementById("hls-player");
            const videoSrc = currentLecture.vodUrl || currentLecture.videoUrl;

            if (videoSrc && videoSrc.endsWith(".m3u8") && window.Hls && Hls.isSupported()) {
                const hls = new Hls();
                hls.loadSource(videoSrc);
                hls.attachMedia(video);
                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    if (resume.lastPosition > 0 && !resume.completed) {
                        video.currentTime = resume.lastPosition;
                    }
                    video.play().catch(() => {});
                });
            } else if (videoSrc) {
                video.src = videoSrc;
                video.addEventListener("loadedmetadata", () => {
                    if (resume.lastPosition > 0 && !resume.completed) {
                        video.currentTime = resume.lastPosition;
                    }
                });
            }

            // Speed controls
            const speeds = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2.0];
            let currentSpeedIdx = 2;

            // Heartbeat every 30 seconds
            heartbeatInterval = setInterval(async () => {
                if (video.paused || video.ended) return;
                try {
                    const result = await window.Api.heartbeat(lectureId, {
                        currentPosition: Math.floor(video.currentTime),
                        watchedSeconds: 30,
                        playbackSpeed: video.playbackRate
                    });
                    if (result.lectureJustCompleted) {
                        const checkEl = document.getElementById("check-" + lectureId);
                        if (checkEl) checkEl.textContent = "✓";
                        loadSidebarProgress(courseId, lectures);
                    }
                } catch (_) {}
            }, 30000);

            // On video ended
            video.addEventListener("ended", async () => {
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
        };
    };

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
