(function () {
    window.Pages = window.Pages || {};

    window.Pages.home = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();

        app.innerHTML = Components.page(user, Components.loading());

        try {
            const [courses, enrollments] = await Promise.all([
                window.Api.getCourses(),
                user ? window.Api.getMyLearning() : Promise.resolve([])
            ]);

            const inProgress = enrollments.filter(e => e.status === "IN_PROGRESS").slice(0, 4);
            const recentCourses = courses.slice(0, 6);

            let html = '<h1>대시보드</h1>';

            if (inProgress.length > 0) {
                html += '<section class="home-section"><h2>학습 중인 과정</h2><div class="enrollment-grid">';
                inProgress.forEach(e => { html += Components.enrollmentCard(e); });
                html += '</div><a href="#/my-learning" class="link-more">전체 보기 &rarr;</a></section>';
            }

            html += '<section class="home-section"><h2>추천 과정</h2>';
            if (recentCourses.length === 0) {
                html += '<p class="text-muted">등록된 과정이 없습니다.</p>';
            } else {
                html += '<div class="course-grid">';
                recentCourses.forEach(c => { html += Components.courseCard(c); });
                html += '</div><a href="#/courses" class="link-more">전체 과정 보기 &rarr;</a>';
            }
            html += '</section>';

            app.innerHTML = Components.page(user, html);
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
