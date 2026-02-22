(function () {
    window.Pages = window.Pages || {};

    window.Pages.courses = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const courses = await window.Api.getCourses();

            const categories = [...new Set(courses.map(c => c.category).filter(Boolean))];

            let html = `
            <div class="courses-header">
                <h1>과정 목록</h1>
                <div class="courses-filters">
                    <input type="text" id="course-search" placeholder="과정 검색..." class="search-input" />
                    <select id="course-category" class="filter-select">
                        <option value="">전체 카테고리</option>
                        ${categories.map(c => `<option value="${Components.escapeHtml(c)}">${Components.escapeHtml(c)}</option>`).join('')}
                    </select>
                </div>
            </div>
            <div id="course-list" class="course-grid">
                ${courses.length === 0 ? '<p class="text-muted">등록된 과정이 없습니다.</p>' : courses.map(c => Components.courseCard(c)).join('')}
            </div>`;

            app.innerHTML = Components.page(user, html);

            const searchInput = document.getElementById("course-search");
            const categorySelect = document.getElementById("course-category");
            let allCourses = courses;

            async function filterCourses() {
                const search = searchInput.value.trim();
                const category = categorySelect.value;
                try {
                    let filtered;
                    if (search) {
                        filtered = await window.Api.getCourses({ search });
                    } else if (category) {
                        filtered = await window.Api.getCourses({ category });
                    } else {
                        filtered = allCourses;
                    }
                    const list = document.getElementById("course-list");
                    if (list) {
                        list.innerHTML = filtered.length === 0
                            ? '<p class="text-muted">검색 결과가 없습니다.</p>'
                            : filtered.map(c => Components.courseCard(c)).join('');
                    }
                } catch (_) {}
            }

            let debounceTimer;
            searchInput.addEventListener("input", () => {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(filterCourses, 300);
            });
            categorySelect.addEventListener("change", filterCourses);
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
