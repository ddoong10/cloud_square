(function () {
    window.Pages = window.Pages || {};

    window.Pages.myCertificates = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const certificates = await window.Api.getMyCertificates();

            let html = '<h1>내 이수증</h1>';

            if (certificates.length === 0) {
                html += '<p class="text-muted">발급된 이수증이 없습니다. 과정을 수료하면 이수증을 발급받을 수 있습니다.</p>';
            } else {
                html += '<div class="certificate-grid">';
                certificates.forEach(c => { html += Components.certificateCard(c); });
                html += '</div>';
            }

            app.innerHTML = Components.page(user, html);
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
