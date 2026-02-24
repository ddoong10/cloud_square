(function () {
    window.Components = {

        navbar(user) {
            const isAdmin = user && (user.role === "ADMIN" || user.role === "INSTRUCTOR");
            return `
            <nav class="navbar">
                <a href="#/" class="nav-brand">LMS</a>
                <div class="nav-links">
                    <a href="#/" class="nav-link">홈</a>
                    <a href="#/courses" class="nav-link">과정</a>
                    ${user ? '<a href="#/my-learning" class="nav-link">내 학습</a>' : ''}
                    ${user ? '<a href="#/my-certificates" class="nav-link">이수증</a>' : ''}
                    ${isAdmin ? '<a href="#/admin/dashboard" class="nav-link nav-admin">관리</a>' : ''}
                </div>
                <div class="nav-actions">
                    ${user
                        ? `<a href="#/profile" class="nav-user">${this.escapeHtml(user.name || user.email)}</a>
                           <button onclick="window.Api.logout(); window.Router.navigate('/login');" class="nav-logout-btn">로그아웃</button>`
                        : '<a href="#/login" class="nav-link">로그인</a>'}
                </div>
            </nav>`;
        },

        courseCard(course) {
            return `
            <div class="course-card" onclick="window.Router.navigate('/courses/${course.id}')">
                <div class="course-card-thumb">
                    ${course.thumbnailUrl
                        ? `<img src="${this.escapeHtml(course.thumbnailUrl)}" alt="${this.escapeHtml(course.title)}" loading="lazy">`
                        : '<div class="course-card-placeholder">COURSE</div>'}
                </div>
                <div class="course-card-body">
                    <h3 class="course-card-title">${this.escapeHtml(course.title)}</h3>
                    ${course.category ? `<span class="course-card-category">${this.escapeHtml(course.category)}</span>` : ''}
                    ${course.instructorName ? `<p class="course-card-instructor">${this.escapeHtml(course.instructorName)}</p>` : ''}
                    <p class="course-card-meta">${course.lectureCount || 0}개 강의</p>
                </div>
            </div>`;
        },

        progressBar(percent) {
            return `
            <div class="progress-bar-container">
                <div class="progress-bar-fill" style="width: ${percent}%"></div>
                <span class="progress-bar-text">${percent}%</span>
            </div>`;
        },

        enrollmentCard(enrollment) {
            return `
            <div class="enrollment-card" onclick="window.Router.navigate('/courses/${enrollment.courseId}')">
                <div class="enrollment-card-thumb">
                    ${enrollment.courseThumbnailUrl
                        ? `<img src="${this.escapeHtml(enrollment.courseThumbnailUrl)}" alt="${this.escapeHtml(enrollment.courseTitle)}" loading="lazy">`
                        : '<div class="course-card-placeholder">COURSE</div>'}
                </div>
                <div class="enrollment-card-body">
                    <h3>${this.escapeHtml(enrollment.courseTitle)}</h3>
                    ${this.progressBar(enrollment.progressPercent)}
                    <span class="enrollment-status enrollment-status-${enrollment.status.toLowerCase()}">${enrollment.status === 'COMPLETED' ? '수료' : '학습 중'}</span>
                </div>
            </div>`;
        },

        certificateCard(cert) {
            return `
            <div class="certificate-card">
                <div class="certificate-card-body">
                    <h3>${this.escapeHtml(cert.courseName)}</h3>
                    <p>수료자: ${this.escapeHtml(cert.userName || '-')}</p>
                    <p>발급일: ${cert.issuedAt ? cert.issuedAt.substring(0, 10) : '-'}</p>
                    <p class="cert-number">${cert.certificateNumber}</p>
                </div>
                <div class="certificate-card-actions">
                    <button class="btn btn-primary" onclick="window.Api.downloadCertificatePdf('${cert.certificateNumber}')">PDF 다운로드</button>
                    <a href="#/verify/${cert.certificateNumber}" class="btn btn-secondary">진위 확인</a>
                </div>
            </div>`;
        },

        modal(title, content, onClose) {
            const overlay = document.createElement("div");
            overlay.className = "modal-overlay";
            overlay.innerHTML = `
                <div class="modal-box">
                    <div class="modal-header">
                        <h3>${title}</h3>
                        <button class="modal-close">&times;</button>
                    </div>
                    <div class="modal-content">${content}</div>
                </div>`;
            overlay.querySelector(".modal-close").addEventListener("click", () => {
                overlay.remove();
                if (onClose) onClose();
            });
            overlay.addEventListener("click", (e) => {
                if (e.target === overlay) {
                    overlay.remove();
                    if (onClose) onClose();
                }
            });
            document.body.appendChild(overlay);
            return overlay;
        },

        loading() {
            return '<div class="loading-spinner"><div class="spinner"></div><p>로딩 중...</p></div>';
        },

        error(message) {
            return `<div class="error-box">${this.escapeHtml(message)}</div>`;
        },

        escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        },

        page(user, content) {
            return this.navbar(user) + '<div class="page-container">' + content + '</div>';
        }
    };
})();
