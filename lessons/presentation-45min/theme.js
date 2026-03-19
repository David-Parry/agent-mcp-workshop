// Theme toggle — persists choice in localStorage
(function() {
    var saved = localStorage.getItem('mcp-pres-theme');
    if (saved) document.documentElement.setAttribute('data-theme', saved);

    document.addEventListener('DOMContentLoaded', function() {
        var btn = document.getElementById('theme-toggle');
        if (!btn) return;

        function updateLabel() {
            var current = document.documentElement.getAttribute('data-theme');
            btn.textContent = current === 'light' ? 'Dark' : 'Light';
        }

        updateLabel();

        btn.addEventListener('click', function(e) {
            e.preventDefault();
            var current = document.documentElement.getAttribute('data-theme');
            var next = current === 'light' ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('mcp-pres-theme', next);
            updateLabel();
        });
    });
})();
