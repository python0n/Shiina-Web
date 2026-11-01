// Libraries only some pages need (charts, the userpage editor, the country picker) are loaded on
// demand instead of on every page. Each file is fetched once; later calls reuse the same promise.
const shiinaLoaded = {};
function shiinaLoad(urls) {
    const v = window.SHIINA_ASSET_V ? '?v=' + window.SHIINA_ASSET_V : '';
    return [].concat(urls).reduce((chain, url) => chain.then(() => {
        if (!shiinaLoaded[url]) {
            shiinaLoaded[url] = new Promise((resolve) => {
                let el;
                if (url.endsWith('.css')) {
                    el = document.createElement('link');
                    el.rel = 'stylesheet';
                    el.href = url + v;
                } else {
                    el = document.createElement('script');
                    el.src = url + v;
                }
                el.onload = resolve;
                el.onerror = resolve;
                document.head.appendChild(el);
            });
        }
        return shiinaLoaded[url];
    }), Promise.resolve());
}
function shiinaWithChart(fn) {
    if (window.Chart) { fn(); return; }
    shiinaLoad('/js/chart.min.js').then(fn);
}

// escape text before putting it into innerHTML / template strings
function shiinaEscape(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
}

let tooltipList = []; // Keep track of initialized tooltips
var out;
var out2;
var turnstileId = null;


// Save scroll position instantly on navigation

document.addEventListener("turbo:before-visit", function (event) {
    // Save scroll if navigating to the same path (ignore query params)
    const currentUrl = new URL(window.location.href);
    const nextUrl = new URL(event.detail.url, window.location.origin);
    if (currentUrl.pathname === nextUrl.pathname) {
        sessionStorage.setItem("shiina-scroll", window.scrollY);
    } else {
        sessionStorage.removeItem("shiina-scroll");
    }
});

loadEventTurbo = document.addEventListener("turbo:load", function () {
    // Restore scroll position if available (for same path)
    const savedScroll = sessionStorage.getItem("shiina-scroll");
    if (savedScroll !== null) {
        window.scrollTo(0, parseInt(savedScroll));
        sessionStorage.removeItem("shiina-scroll");
    }

    performOnboarding();
    loadUserPage();
    loadComments();
    const nodesWithTimestamp = document.querySelectorAll('[data-timestamp]');
    const nodesArray = Array.from(nodesWithTimestamp);

    let relNodes = document.querySelectorAll('[data-shiina-call]');
    const relArray = Array.from(relNodes);

    handleRelUpdate(relArray);

    loadContrySelectorIfPresent();
    loadTurnstileIfPresent();

    if (document.getElementById('video') != undefined) {
        const video = document.getElementById('video');
        loadVideoWithDelay(video);
    }

    initUserpageEditor();

    // like osu-web: the server prints UTC, the browser shows it in the viewer's own time zone
    nodesArray.forEach(node => {
        const date = parseShiinaTime(node.getAttribute('data-timestamp'));
        if (!date) return; // keep the server text
        const full = formatLocalTime(date);
        if (node.getAttribute('data-timestamp-format') === 'local') {
            node.textContent = full;
        } else {
            node.textContent = timeUntil(date.getTime() / 1000, true);
            node.title = full;
        }
    });

    updateTooltips();
});

function performOnboarding() {
    const onboarding = document.getElementById('onboarding');
    if (onboarding !== null) {
        // store the interval ID
        const intervalId = setInterval(sendRequest, 10000);

        function sendRequest() {
            fetch('/api/v1/onboarding', { method: 'GET' })
                .then(res => res.json())
                .then(data => {
                    if (data.priv !== 1) {
                        // stop the interval using its ID
                        clearInterval(intervalId);
                        Turbo.visit('/u/' + data.id);
                    }
                })
                .catch(err => console.error('OnBoarding Request failed', err));
        }
    }
}

function initUserpageEditor() {
    if(document.getElementById('editor') != undefined) {
        shiinaLoad(['/css/wbbtheme.css', '/css/wysibb-theme.css', '/js/jquery.wysibb.min.js']).then(() => {
            if(document.getElementsByClassName('wysibb').length == 0 && document.getElementById('editor')) {
                var wbbOpt = {
                    buttons: "bold,italic,underline,|,img,link,|,table,removeFormat",
                };
                $("#editor").wysibb(wbbOpt);
            }
        });
    }
}

function loadContrySelectorIfPresent() {
    if (document.getElementById('country-selector') != undefined) {
        shiinaLoad(['/css/select2.min.css', '/js/select2.min.js']).then(initCountrySelect2);
    }
}

function initCountrySelect2() {
    if (document.getElementById('country-selector') != undefined) {
        $('#country-selector').select2({
            templateResult: formatCountry,
            templateSelection: formatCountry,
            minimumResultsForSearch: Infinity // Hide search if unnecessary
        });

        function formatCountry(country) {
            // Ensure country.id is valid
            if (!country.id) {
                return country.text;
            }

            // Get the flag URL
            var flagUrl = $(country.element).data('flag');
            var countryName = country.text;

            // Create the HTML using jQuery parseHTML or native DOM
            var $countryHtml = $(
                '<span class="text-white">' +
                '<img src="' + flagUrl + '" style="width: 20px; height: 15px; margin-right: 8px;">' +
                countryName +
                '</span>'
            );

            return $countryHtml;
        }
    }
}

function handleRelUpdate(relArray) {
    relArray.forEach(node => {
        let user = node.getAttribute('data-user');
        node.onclick = function () {
            fetch("/api/v1/update_rel?u="+user, { method: 'POST' })
            .then(response => response.text())
            .then(response => {
                Turbo.visit(window.location.href);
            })
        }
    });
}

submitStartTurbo = document.addEventListener('turbo:submit-start', function () {
    Turbo.navigator.delegate.adapter.showProgressBar();
});



winEvent = document.addEventListener('resize', function () {
    if (out) {
        out.resize();
    }
});

// Remove tooltips and event listeners when navigating back
unloadEventTurbo = document.addEventListener("turbo:before-cache", function () {
    Turbo.navigator.delegate.adapter.showProgressBar();

    // Properly dispose of all tooltips before caching
    tooltipList.forEach(tooltip => {
        tooltip.dispose();
    });
    tooltipList = [];

    if (out != null) {
        out.destroy();
        out = null; // Clear reference
    }

    unloadTurnstileIfPresent();

    // Clean up event listeners
    window.removeEventListener('resize', winEvent);
    document.removeEventListener("turbo:load", loadEventTurbo);
    document.removeEventListener("turbo:submit-start", submitStartTurbo);
    document.removeEventListener("turbo:before-cache", unloadEventTurbo);
});

function unloadTurnstileIfPresent() {
    if(turnstileId != null && document.getElementById("turnstile")) {
        if(document.getElementById("turnstile").innerHTML.length > 0) {
            console.log("Removing Turnstile");
            turnstile.remove(turnstileId);
        }
    }
}

function loadTurnstileIfPresent() {
    const el = document.getElementById("turnstile");

    if (!el) return;
    if (typeof turnstile === "undefined") return;
    if (el.dataset.loaded === "true") return;

    try {
        turnstileId = turnstile.render("#turnstile", {
            sitekey: turnstileToken
        });

        el.dataset.loaded = "true";
        console.log("Turnstile rendered");
    } catch (e) {
        console.error(e);
    }
}

// Robust loader: retry a few times if the external script or token isn't available yet.
function loadTurnstileIfPresentWithRetry() {
    const el = document.getElementById("turnstile");
    if (!el) return;
    if (el.dataset.loaded === "true") return;

    let attempts = 0;
    const maxAttempts = 20;
    const delayMs = 200;

    const tryRender = () => {
        // token may be provided as global `turnstileToken` or as data attribute on the element
        const token = typeof turnstileToken !== "undefined" ? turnstileToken : el.dataset.token;

        if (typeof turnstile !== "undefined" && token) {
            try {
                if (typeof turnstile.render === 'function') {
                    turnstileId = turnstile.render("#turnstile", { sitekey: token });
                    el.dataset.loaded = "true";
                    console.log("Turnstile rendered");
                } else if (typeof turnstile === 'function') {
                    // some builds may expose a callable; try as a fallback
                    try {
                        turnstileId = turnstile("#turnstile", { sitekey: token });
                        el.dataset.loaded = "true";
                        console.log("Turnstile rendered via function fallback");
                    } catch (err) {
                        console.warn("Turnstile API present but no callable render; aborting retries.", err);
                        el.dataset.loaded = "failed";
                    }
                } else {
                    console.warn("Turnstile API present but `render` is not a function; aborting retries.");
                    el.dataset.loaded = "failed";
                }
            } catch (e) {
                console.error("Turnstile render failed:", e);
            }
            return;
        }

        attempts += 1;
        if (attempts < maxAttempts) {
            setTimeout(tryRender, delayMs);
        } else {
            console.warn("Turnstile not available after retries");
        }
    };

    tryRender();
}

// Keep the original function name used in the code path but route to the robust loader.
function loadTurnstileIfPresent() {
    loadTurnstileIfPresentWithRetry();
}

function loadComments(firstLoad = true) {
    if(document.getElementById('commentLoadable') == undefined) return;

    let offset = document.getElementById('offsetComments');

    if(firstLoad) {
        offset.value = 0;
        document.getElementById('commentLoadable').innerHTML = '';
    }

    fetch("/api/v1/get_comments?offset=" + offset.value + "&target=" + commentsModule.target+"&id=" + commentsModule.id)
    .then(response => response.json())
        .then(data => {
            let container = document.getElementById('commentLoadable');
            if(!data.hasNextPage) {
                let btn = document.getElementById('commentButton');
                btn.classList.add('disabled');
            }

            data.comments.forEach(comment => {
                let element = document.createElement('div');
                element.innerHTML = loadCommentPanel(comment.user, comment, comment.time * 100000);
                container.appendChild(element);
            }
            )

        }
    );
}

function changeFirstPlacesSort() {
    let sort = document.getElementById('firstPlacesSort').value;
    reqUrl = reqUrl.replace(/sort=(asc|desc)/, 'sort=' + sort);
    loadFirstPlaces(reqUrl, true);
}

function loadFirstPlaces(apiUrl, firstLoad = true) {
    let offset = document.getElementById('offsetFirstPlaces');

    if(firstLoad) {
        offset.value = 0;
    }

    apiUrl += offset.value;

    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {

            let container = document.getElementById('firstPlaces');
            let countElement = document.getElementById('firstPlacesCount');

            countElement.innerHTML = '(' + data.count + ')';
            if(!data.hasNextPage) {
                let btn = document.getElementById('firstPlacesButton');
                btn.classList.add('disabled');
            }

            if(firstLoad)
                container.innerHTML = '';
            data.firstPlaces.forEach(firstPlace => {
                // Create a new element for each first place
                let element = document.createElement('div');
                element.innerHTML = loadScorePanel(firstPlace.grade, firstPlace.map_id, firstPlace, firstPlace.pp, firstPlace.acc, firstPlace.max_combo, firstPlace.play_time, firstPlace.map_name, firstPlace.map_set_id, firstPlace.score_id,firstPlace.mods);
                container.appendChild(element);
            });
            updateTooltips();
        })
        .catch(error => console.error('Error:', error));
}


function loadBestScores(apiUrl, firstLoad = true) {
    let offset = document.getElementById('offsetBestScores');

    if(firstLoad) {
        offset.value = 0;
    }

    apiUrl += offset.value + '&scope=best';

    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {

            let container = document.getElementById('bestScores');

            if(!data.hasNextPage) {
                let btn = document.getElementById('bestScoresButton');
                btn.classList.add('disabled');
            }

            if(firstLoad)
                container.innerHTML = '';
            data.scores.forEach(firstPlace => {

                let element = document.createElement('div');
                element.innerHTML = loadScorePanel(firstPlace.grade, firstPlace.map_id, firstPlace, firstPlace.pp, firstPlace.acc, firstPlace.max_combo, firstPlace.play_time, firstPlace.map_name, firstPlace.map_set_id, firstPlace.score_id,firstPlace.mods, firstPlace.weight, firstPlace.weight_pp);
                container.appendChild(element);
            });
            updateTooltips();
        })
        .catch(error => console.error('Error:', error));
}


function loadLastScores(apiUrl, firstLoad = true) {
    let offset = document.getElementById('offsetLastScores');

    if(firstLoad) {
        offset.value = 0;
    }

    apiUrl += offset.value + '&scope=recent';

    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {

            let container = document.getElementById('lastScores');

            if(!data.hasNextPage) {
                let btn = document.getElementById('lastScoresButton');
                btn.classList.add('disabled');
            }

            if(firstLoad)
                container.innerHTML = '';
            data.scores.forEach(firstPlace => {

                let element = document.createElement('div');
                element.innerHTML = loadScorePanel(firstPlace.grade, firstPlace.map_id, firstPlace, firstPlace.pp, firstPlace.acc, firstPlace.max_combo, firstPlace.play_time, firstPlace.map_name, firstPlace.map_set_id, firstPlace.score_id,firstPlace.mods);
                container.appendChild(element);
            });
            updateTooltips();
        })
        .catch(error => console.error('Error:', error));
}

function updateTooltips() {
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
}


function loadLazyLoadImage(img) {
    if (img.complete && img.naturalHeight !== 0) {
        img.classList.add('loaded');
        img.previousElementSibling.style.display = 'none';
    } else {
        setTimeout(() => {
            img.classList.add('loaded');
            img.previousElementSibling.style.display = 'none';
        }, 300);
    }
}

function loadVideoWithDelay(video) {
    const loader = document.getElementById('spinner');
    if (!video.classList.contains('loaded')) {
        video.style.dislay = 'none';
        loader.style.display = 'block';
        setTimeout(() => {
            video.classList.add('loaded');
            loader.style.display = 'none';
            video.style.display = 'block';
        }, 3000);
    }
}


function lazyLoadNoImage(img, icon) {
    if (img.complete && img.naturalHeight !== 0) {
        img.classList.add('loaded');
        img.previousElementSibling.style.display = 'none';
    } else {
        random = Math.floor(Math.random() * 1000);
        setTimeout(() => {
            img.src = icon;
            img.classList.add('loaded');
            img.previousElementSibling.style.display = 'none';
        }, random);
    }
}

// translated UI text from window.SHIINA_T (filled by base.html), English fallback
function tr(key, fallback) {
    const all = window.SHIINA_T || {};
    return Object.prototype.hasOwnProperty.call(all, key) ? all[key] : fallback;
}

// unix seconds or a "YYYY-MM-DD HH:MM:SS" date from the database (stored in UTC)
function parseShiinaTime(raw) {
    if (raw == null) return null;
    raw = String(raw).trim();
    if (raw === '') return null;
    let date;
    if (/^\d+$/.test(raw)) {
        date = new Date(parseInt(raw, 10) * 1000);
    } else {
        const hasZone = /([zZ]|[+-]\d\d:?\d\d)$/.test(raw);
        date = new Date(raw.replace(' ', 'T') + (hasZone ? '' : 'Z'));
    }
    return isNaN(date.getTime()) ? null : date;
}
// full date and time in the viewer's time zone and site language
function formatLocalTime(date) {
    const lang = document.documentElement.lang || 'en';
    try {
        return new Intl.DateTimeFormat(lang, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
    } catch (e) {
        return date.toLocaleString();
    }
}
function timeUntil(dateInput, unix) {
    let inputDate;
    if (unix) {
        inputDate = new Date(dateInput * 1000);
    } else {
        inputDate = new Date(dateInput.replace(' ', 'T') + 'Z');
    }

    if (isNaN(inputDate.getTime())) return String(dateInput);
    const currentDate = new Date();
    const differenceInSeconds = Math.floor((inputDate - currentDate) / 1000);

    const isPast = differenceInSeconds < 0;
    const absDifferenceInSeconds = Math.abs(differenceInSeconds);

    const timeUnits = [
        { unit: 'year', seconds: 29030400 },
        { unit: 'month', seconds: 2419200 },
        { unit: 'week', seconds: 604800 },
        { unit: 'day', seconds: 86400 },
        { unit: 'hour', seconds: 3600 },
        { unit: 'minute', seconds: 60 },
        { unit: 'second', seconds: 1 }
    ];

    // Intl.RelativeTimeFormat knows the plural forms of every language ("5 minut temu", "5 минут назад")
    const lang = document.documentElement.lang || 'en';
    for (const { unit, seconds } of timeUnits) {
        const value = Math.floor(absDifferenceInSeconds / seconds);
        if (value > 0 || unit === 'second') {
            if (typeof Intl !== 'undefined' && Intl.RelativeTimeFormat) {
                return new Intl.RelativeTimeFormat(lang, { numeric: 'always' }).format(isPast ? -value : value, unit);
            }
            const text = `${value} ${unit}${value !== 1 ? 's' : ''}`;
            return isPast ? text + ' ago' : text + ' from now';
        }
    }
    return '';
}

function removeParam(param) {
    let url = new URL(window.location.href);
    url.searchParams.delete(param);
    Turbo.visit(url);
}


function selectParam(param, value) {
    let url = new URL(window.location.href);
    url.searchParams.set(param, value);
    if (param != 'page' || param == 'country') {
        url.searchParams.set('page', 1);
    }
    if (param == 'mode') {
        url.searchParams.delete('country');
    }
    Turbo.visit(url);
}

function loadUserPage() {
    let loadedNew = document.getElementById('firstLoad');
    if(loadedNew == null) return;

    if(loadedNew.value == 'true') {
        loadedNew.value = 'false';
         // Load score panel first
        loadFirstPlaces(reqUrl);
        if (document.getElementById('pinnedScores')) loadPinnedScores(reqUrlScores);
        loadBestScores(reqUrlScores);
        loadLastScores(reqUrlScores);
    }

    initPlayCountGraph();
}

function initPlayCountGraph() {
    if (!window.Chart) { shiinaWithChart(initPlayCountGraph); return; }

    fetch(rankGraphUrl)
    .then(response => response.json())
    .then(data => {

        let values = [];
        let dataLabels = [];

        data.forEach(element => {
            values.push(element.rank);
            dataLabels.push(element.date);
        });

        if(data.length == 0) {
            document.getElementById('rankDiv').innerHTML = "<span class='text-center'>No rank data available</span>";
        }

        let ctx = document.getElementById('rankChart');
        if(out2) {
            out2.destroy();
        }

        out2 = new Chart(ctx, {
            type: 'line',
            data: {
                labels: dataLabels,
                datasets: [{
                    label: tr('js.rank', 'Rank'),
                    data: values,
                    borderColor: getBsPrimaryColor(),
                    tension: 0.1
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    tooltip: {
                        mode: 'nearest',
                        intersect: false
                    },
                    legend: {
                        display: false
                    }
                },
                scales: {
                    x: {
                        display: false
                    },
                    y: {
                        display: false,
                        reverse: true
                    }
                }
            }
        });

    });

    fetch(playCountGraphUrl)
    .then(response => response.json())
    .then(data => {
        let values = [];
        let dataLabels = [];

        data.forEach(element => {
            values.push(element.plays);
            dataLabels.push(element.date);
        });

        let ctx = document.getElementById('playCountGraph');

        if (out) {
            out.destroy();
        }

        out = new Chart(ctx, {
            type: 'line',
            data: {
                labels: dataLabels,
                datasets: [{
                    label: '# of Plays',
                    data: values,
                    borderColor: getBsPrimaryColor(),
                    borderWidth: 2,
                    tension: 0.1,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                aspectRatio: 5,
                scales: {
                    x: {
                        grid: {
                            color: bootstrapTextTransparent
                        },
                        ticks: {
                            color: bootstrapTextColor
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: bootstrapTextTransparent
                        },
                        ticks: {
                            color: bootstrapTextColor
                        }
                    }
                },
                plugins: {
                    tooltip: {
                        mode: 'nearest',
                        intersect: false
                    },
                    legend: {
                        display: false
                    }
                }
            }});

    });



}


function loadCommentPanel(user, comment, time) {
    const wrapper = document.createElement('div');
    wrapper.className = 'd-flex align-items-start p-3 border-bottom';

    const avatarWrapper = document.createElement('div');
    avatarWrapper.className = 'me-3';

    const avatar = document.createElement('img');
    avatar.className = 'rounded mt-1';
    avatar.src = `${avatarServer}/${encodeURIComponent(user.id)}`;
    avatar.alt = 'Avatar';
    avatar.width = 40;
    avatar.height = 40;
    avatarWrapper.appendChild(avatar);

    const content = document.createElement('div');
    content.className = 'flex-grow-1';

    const header = document.createElement('div');
    header.className = 'd-flex align-items-center mb-2';

    const link = document.createElement('a');
    link.href = `/u/${encodeURIComponent(user.id)}`;
    link.className = `text-decoration-none fw-medium no-a fw-bold${comment.supporter ? ' supporter' : ''}`;
    link.textContent = user.name;
    header.appendChild(link);

    user.groups.forEach(group => {
        const badge = document.createElement('span');
        badge.className = 'badge ms-2 shiina-badge bg-light bg-opacity-25 text-white py-1 rounded-pill pe-3';

        const emoji = document.createElement('span');
        emoji.className = 'groupEmoji me-2';
        emoji.textContent = group.emoji;

        badge.appendChild(emoji);
        badge.appendChild(document.createTextNode(group.name));

        header.appendChild(badge);
    });

    const body = document.createElement('div');
    body.className = 'bg-secondary bg-opacity-10 text-body rounded';
    body.textContent = comment.comment;

    content.appendChild(header);
    content.appendChild(body);

    wrapper.appendChild(avatarWrapper);
    wrapper.appendChild(content);

    return wrapper.outerHTML;
}
function loadScorePanel(
    grade,
    mapId,
    score,
    pp,
    acc,
    maxCombo,
    playTime,
    name,
    setId,
    scoreId,
    mods,
    weight = 0,
    weight_pp = 0
) {
    let pinButton = ``;
    let dragHandle = ``;
    if (typeof loggedUserId !== "undefined" && loggedUserId > 0 && score.user_id === loggedUserId && typeof score.pinned !== "undefined") {
        const isPinned = score.pinned == 1;
        pinButton = `<a onclick="event.stopPropagation(); togglePinCard(${scoreId}, ${isPinned ? 1 : 0});"
           class="osu-action-btn"
           title="${isPinned ? tr('js.unpin_score', 'Unpin Score') : tr('js.pin_score', 'Pin Score')}"
           style="cursor:pointer;${isPinned ? 'background:rgba(241,196,15,0.35);border-color:#f1c40f;' : ''}">
            <i class="fas fa-thumbtack"></i>
        </a>`;
        if (isPinned) {
            dragHandle = `<span class="osu-action-btn pin-drag-handle" title="${tr('js.drag_reorder', 'Drag to reorder')}"
               style="cursor:grab;" onclick="event.stopPropagation();" onmousedown="event.stopPropagation();">
                <i class="fas fa-grip-vertical"></i>
            </span>`;
        }
    }
    const beatmapImg = `https://assets.ppy.sh/beatmaps/${setId}/covers/cover.jpg?1650681317`;
    // map names come from uploaded .osu files (BSS), so never trust them as HTML
    const sanitizedName = shiinaEscape(String(name).replace('.osu', ''));
    grade = shiinaEscape(grade);
    mods = Array.isArray(mods) ? mods.map(shiinaEscape) : mods;

    // Helper for conditional elements
    const createBadge = (condition, html) => condition ? html : '';

    const modsDisplay = createBadge(
        mods.length > 0,
        ` <span class="mod-pill badge bg-warning bg-opacity-50 text-warning border border-warning">${mods.join(", ")}</span>`
    );

    const weightDisplay = createBadge(
        weight > 0,
        `<div class="weight-badge badge bg-info bg-opacity-25 text-info border border-info" title="${tr('js.weight_tip', 'Weight contribution to profile pp')}">
            <span>${weight}%</span>
            <span class="fw-bold">${weight_pp}pp</span>
        </div>`
    );

    const gradeDisplay = grade.toLowerCase() === 'f'
        ? `<div class="osu-grade grade-f"><i class="fas fa-times"></i></div>`
        : `<div class="osu-grade"><img src="/img/ranking/ranking-${grade}.png" alt="Grade ${grade}"></div>`;

    let downloadButton = ``;

    if(score.grade != "F") {
        downloadButton = `<a download onclick="event.stopPropagation();" href="${apiUrl}/v1/get_replay?id=${scoreId}"
           class="osu-action-btn osu-download-btn"
           title="${tr('js.download_replay', 'Download Replay')}"
           onmouseover="this.style.background='rgba(46,204,113,0.3)'; this.style.borderColor='#2ecc71';"
           onmouseout="this.style.background='rgba(255,255,255,0.1)'; this.style.borderColor='rgba(255,255,255,0.2)';">
            <i class="fas fa-download"></i>
        </a>`;
    }

   return `
    <div class="osu-score-card mb-3" data-score-id="${scoreId}" role="link" tabindex="0" style="cursor:pointer;" onclick="window.location='/scores/${scoreId}'" onmousedown="if(event.button===1){event.preventDefault();window.open('/scores/${scoreId}','_blank');}" onkeydown="if(event.key==='Enter'||event.key===' ') { event.preventDefault(); window.location='/scores/${scoreId}'; }">
        <div class="osu-score-container">
            <!-- Beatmap Background -->
            <div class="osu-beatmap-bg" style="background-image: url('${beatmapImg}')"></div>

            <!-- Content Overlay -->
            <div class="osu-score-content">
                <!-- Grade Badge -->
                ${gradeDisplay}

                <!-- Score Info -->
                <div class="osu-score-info">
                    <div class="osu-beatmap-title">
                        ${sanitizedName}${modsDisplay}
                    </div>

                    <div class="osu-score-stats">
                        <div class="osu-pp-display">
                            <span title="${pp.toFixed(3)}pp">${Math.round(pp)}</span><span class="pp-unit">pp</span>
                        </div>
                        <div class="osu-acc-display">
                            ${parseFloat(acc).toFixed(2)}%
                        </div>

                    </div>

                    ${weightDisplay}
                </div>

                <!-- Action Buttons -->
                <div class="osu-action-buttons">
                    ${dragHandle}${pinButton}${downloadButton}
                </div>
            </div>
        </div>
    </div>
`;

}

function getBsPrimaryColor() {
    // theme primary colour; does not depend on any .bg-primary element being on the page
    const fromVar = getComputedStyle(document.documentElement).getPropertyValue('--bs-primary').trim();
    if (fromVar) return fromVar;
    const element = document.getElementsByClassName('bg-primary')[0];
    return element ? getComputedStyle(element).backgroundColor : '#0d6efd';
}

function getBootstrapTextColor() {
    const element = document.getElementById('text');
    const computedStyle = window.getComputedStyle(element);
    return computedStyle.color;
}

function getBootstrapTextTransparent() {
    let color = getBootstrapTextColor();
    color = color.slice(0, -1) + ', 0.2)'; // Remove last char and add transparency
    return color;
}

function addLoader(button) {
    button.disabled = true; // Disable the button
    button.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>' + tr('js.loading', 'Loading...');
}

function removeLoader(button, originalText) {
    button.disabled = false; // Re-enable the button
    button.innerHTML = originalText; // Restore the original text
}

function loadMore() {
    let button = document.getElementById('firstPlacesButton');
    let originalText = button.innerHTML;
    addLoader(button); // Add loader to button

    let offset = document.getElementById('offsetFirstPlaces');
    offset.value = parseInt(offset.value) + 5;

    // Keep the loader visible for at least 1 second
    setTimeout(() => {
        loadFirstPlaces(reqUrl, false); // Call the loading function

        // Remove loader after loading process (1 second delay before executing)
        removeLoader(button, originalText);
    }, 500); // 1-second delay
}

function loadMoreScores() {
    let button = document.getElementById('bestScoresButton');
    let originalText = button.innerHTML;
    addLoader(button); // Add loader to button

    let offset = document.getElementById('offsetBestScores');
    offset.value = parseInt(offset.value) + 5;

    // Keep the loader visible for at least 1 second
    setTimeout(() => {
        loadBestScores(reqUrlScores, false); // Call the loading function

        // Remove loader after loading process (1 second delay before executing)
        removeLoader(button, originalText);
    }, 500); // 1-second delay
}

function loadMoreScoresLast() {
    let button = document.getElementById('lastScoresButton');
    let originalText = button.innerHTML;
    addLoader(button); // Add loader to button

    let offset = document.getElementById('offsetLastScores');
    offset.value = parseInt(offset.value) + 5;

    // Keep the loader visible for at least 1 second
    setTimeout(() => {
        loadLastScores(reqUrlScores, false); // Call the loading function

        // Remove loader after loading process (1 second delay before executing)
        removeLoader(button, originalText);
    }, 500); // 1-second delay
}

function loadMoreComments() {
    let button = document.getElementById('commentButton');
    let originalText = button.innerHTML;
    addLoader(button); // Add loader to button

    let offset = document.getElementById('offsetComments');
    offset.value = parseInt(offset.value) + 5;

    // Keep the loader visible for at least 1 second
    setTimeout(() => {
        loadComments(false); // Call the loading function

        // Remove loader after loading process (1 second delay before executing)
        removeLoader(button, originalText);
    }, 500); // 1-second delay
}

function handleClanManage(userid, clanid, action) {
    fetch('/api/v1/manage_cl?userid=' + userid + '&clanid=' + clanid + "&action=" + action, { method: 'POST' })
        .then(response => response.text())
        .then(response => {
            Turbo.visit(window.location.href);
        });
}

function handleClanJoinRequest(clanid, action) {
    fetch('/api/v1/join_clan?clanid=' + clanid + "&action=" + action, { method: 'POST' })
        .then(response => response.text())
        .then(response => {
            Turbo.visit(window.location.href);
        });
}

function handleClanLeave(clanid) {
    fetch('/api/v1/leave_clan?clanid=' + clanid, { method: 'POST' })
        .then(response => response.text())
        .then(response => {
            Turbo.visit(window.location.href);
        });
}

function loadPinnedScores(apiUrl, firstLoad = true) {
    let offset = document.getElementById('offsetPinnedScores');
    if (!offset) return;
    if (firstLoad) offset.value = 0;
    fetch(apiUrl + offset.value + '&scope=pinned')
        .then(response => response.json())
        .then(data => {
            let section = document.getElementById('pinnedScoresSection');
            let container = document.getElementById('pinnedScores');
            let btn = document.getElementById('pinnedScoresButton');
            if (firstLoad) container.innerHTML = '';
            if (data.hasNextPage) btn.classList.remove('disabled'); else btn.classList.add('disabled');
            if (firstLoad && data.scores.length === 0) { section.style.display = 'none'; return; }
            section.style.display = '';
            initPinnedSortable();
            data.scores.forEach(s => {
                let element = document.createElement('div');
                element.innerHTML = loadScorePanel(s.grade, s.map_id, s, s.pp, s.acc, s.max_combo, s.play_time, s.map_name, s.map_set_id, s.score_id, s.mods);
                container.appendChild(element);
            });
            updateTooltips();
        })
        .catch(error => console.error('Error:', error));
}

function loadMoreScoresPinned() {
    let button = document.getElementById('pinnedScoresButton');
    let originalText = button.innerHTML;
    addLoader(button);
    let offset = document.getElementById('offsetPinnedScores');
    offset.value = parseInt(offset.value) + 5;
    setTimeout(() => {
        loadPinnedScores(reqUrlScores, false);
        removeLoader(button, originalText);
    }, 500);
}

function togglePinCard(scoreId, currentlyPinned) {
    fetch('/api/v1/pin_score?id=' + scoreId + '&action=' + (currentlyPinned ? 'unpin' : 'pin'), { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            if (data.status !== 'success') { alert(data.message || tr('js.error', 'Error')); return; }
            if (typeof reqUrlScores !== 'undefined') {
                loadPinnedScores(reqUrlScores, true);
                loadBestScores(reqUrlScores, true);
            }
        })
        .catch(error => console.error('Error:', error));
}

function togglePinScore(scoreId, btn) {
    const pinned = btn.dataset.pinned === '1';
    fetch('/api/v1/pin_score?id=' + scoreId + '&action=' + (pinned ? 'unpin' : 'pin'), { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            if (data.status !== 'success') { alert(data.message || tr('js.error', 'Error')); return; }
            btn.dataset.pinned = pinned ? '0' : '1';
            btn.innerHTML = '<i class="fa-solid fa-thumbtack me-2"></i>' + (pinned ? tr('js.pin_score', 'Pin Score') : tr('js.unpin_score', 'Unpin Score'));
        })
        .catch(error => console.error('Error:', error));
}

function ensureSortable(cb) {
    if (window.Sortable) { cb(); return; }
    const s = document.createElement('script');
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/Sortable/1.15.6/Sortable.min.js';
    s.onload = cb;
    document.head.appendChild(s);
}

function savePinnedOrder() {
    const ids = [...document.querySelectorAll('#pinnedScores [data-score-id]')].map(el => el.dataset.scoreId);
    if (!ids.length) return;
    fetch('/api/v1/pin_order?order=' + ids.join(','), { method: 'POST' })
        .then(r => r.json())
        .then(data => { if (data.status !== 'success') alert(data.message || tr('js.error', 'Error')); })
        .catch(error => console.error('Error:', error));
}

function initPinnedSortable() {
    if (window._pinnedSortableInit) return;
    if (typeof loggedUserId === 'undefined' || loggedUserId <= 0) return;
    const m = typeof reqUrlScores !== 'undefined' ? reqUrlScores.match(/id=(\d+)/) : null;
    if (!m || parseInt(m[1]) !== loggedUserId) return;
    const container = document.getElementById('pinnedScores');
    if (!container) return;
    window._pinnedSortableInit = true;
    ensureSortable(() => {
        new Sortable(container, {
            animation: 150,
            handle: '.pin-drag-handle',
            onEnd: () => savePinnedOrder()
        });
    });
}

// music preview on the beatmap list (official sets only, from b.ppy.sh)
let shiinaPreviewAudio = null, shiinaPreviewBtn = null;
function shiinaPreviewStop() {
    if (shiinaPreviewAudio) shiinaPreviewAudio.pause();
    if (shiinaPreviewBtn) shiinaPreviewBtn.querySelector('i').className = 'fas fa-play';
    shiinaPreviewAudio = null;
    shiinaPreviewBtn = null;
}
function shiinaPreview(btn) {
    if (shiinaPreviewBtn === btn) { shiinaPreviewStop(); return; }
    shiinaPreviewStop();
    shiinaPreviewBtn = btn;
    btn.querySelector('i').className = 'fas fa-spinner fa-spin';
    shiinaPreviewAudio = new Audio(btn.dataset.preview);
    shiinaPreviewAudio.volume = 0.45;
    shiinaPreviewAudio.addEventListener('playing', () => { if (shiinaPreviewBtn === btn) btn.querySelector('i').className = 'fas fa-stop'; });
    shiinaPreviewAudio.addEventListener('ended', shiinaPreviewStop);
    shiinaPreviewAudio.addEventListener('error', shiinaPreviewStop);
    shiinaPreviewAudio.play().catch(shiinaPreviewStop);
}
document.addEventListener('turbo:before-cache', shiinaPreviewStop);

// unread private messages on the chat icon in the navbar (website chat, /chat)
if (!window.shiinaChatUnread) (function () {
    var last = 0;
    function apply() {
        var el = document.getElementById('shiina-chat-unread');
        if (!el) return;
        var n = window.shiinaChatUnreadCount || 0;
        el.textContent = n > 99 ? '99+' : String(n);
        el.classList.toggle('d-none', n === 0);
        var bell = document.getElementById('shiina-notif-unread');
        if (bell) {
            var b = window.shiinaNotifUnreadCount || 0;
            bell.textContent = b > 99 ? '99+' : String(b);
            bell.classList.toggle('d-none', b === 0);
        }
    }
    window.shiinaUnreadApply = apply;
    function update(force) {
        if (!document.getElementById('shiina-chat-unread')) return;
        if (!force && Date.now() - last < 20000) { apply(); return; }
        last = Date.now();
        fetch('/api/v1/chat/unread', { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (d) { if (d) { window.shiinaChatUnreadCount = d.unread; window.shiinaNotifUnreadCount = d.notifications || 0; apply(); } })
            .catch(function () {});
    }
    window.shiinaChatUnread = update;
    document.addEventListener('turbo:load', function () { update(false); });
    setInterval(function () { if (!document.hidden) update(true); }, 60000);
    if (document.readyState !== 'loading') update(false);
    else document.addEventListener('DOMContentLoaded', function () { update(false); });
})();

// the bell in the navbar: notifications (replays, #1 lost, BSS maps, comments, account)
if (!window.shiinaNotifInit) (function () {
    window.shiinaNotifInit = true;
    var ICONS = {
        replay_accepted: 'fa-circle-check text-success',
        replay_rejected: 'fa-circle-xmark text-danger',
        first_place_lost: 'fa-crown text-warning',
        map_status: 'fa-music text-info',
        map_comment: 'fa-comment',
        deletion_rejected: 'fa-user-shield'
    };
    function when(sec) {
        var d = new Date(sec * 1000), now = new Date();
        var hm = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
        if (d.toDateString() === now.toDateString()) return hm;
        return ('0' + d.getDate()).slice(-2) + '.' + ('0' + (d.getMonth() + 1)).slice(-2) + '.' + d.getFullYear() + ' ' + hm;
    }
    function render(data) {
        var list = document.getElementById('shiina-notif-list');
        if (!list) return;
        window.shiinaNotifUnreadCount = data.unread || 0;
        if (window.shiinaUnreadApply) window.shiinaUnreadApply();
        list.innerHTML = '';
        if (!data.items || !data.items.length) {
            var empty = document.createElement('div');
            empty.className = 'shiina-notif-empty';
            empty.textContent = list.getAttribute('data-empty') || '';
            list.appendChild(empty);
            return;
        }
        data.items.forEach(function (n) {
            var a = document.createElement('a');
            a.className = 'shiina-notif-item' + (n.read ? '' : ' unread');
            a.href = n.link || '#';
            a.setAttribute('data-notif-id', n.id);
            var icon = document.createElement('i');
            icon.className = 'fa-solid ' + (ICONS[n.type] || 'fa-bell') + ' shiina-notif-icon';
            var body = document.createElement('div');
            body.className = 'shiina-notif-body';
            var text = document.createElement('div');
            text.textContent = n.text;
            var time = document.createElement('div');
            time.className = 'shiina-notif-time';
            time.textContent = when(n.time);
            body.appendChild(text);
            body.appendChild(time);
            a.appendChild(icon);
            a.appendChild(body);
            list.appendChild(a);
        });
    }
    function load() {
        fetch('/api/v1/notifications', { credentials: 'same-origin' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (d) { if (d) render(d); })
            .catch(function () {});
    }
    document.addEventListener('show.bs.dropdown', function (e) {
        if (e.target && e.target.id === 'shiina-notif-toggle') load();
    });
    document.addEventListener('click', function (e) {
        var clear = e.target.closest && e.target.closest('#shiina-notif-clear');
        if (clear) {
            e.preventDefault();
            fetch('/api/v1/notifications/read?all=1', { method: 'POST', credentials: 'same-origin' })
                .then(function () { load(); }).catch(function () {});
            return;
        }
        var item = e.target.closest && e.target.closest('.shiina-notif-item');
        if (item) {
            var id = item.getAttribute('data-notif-id');
            var href = item.getAttribute('href');
            e.preventDefault();
            fetch('/api/v1/notifications/read?id=' + encodeURIComponent(id), { method: 'POST', credentials: 'same-origin', keepalive: true })
                .catch(function () {})
                .then(function () {
                    if (href && href !== '#') {
                        if (window.Turbo) window.Turbo.visit(href); else window.location.href = href;
                    } else {
                        load();
                    }
                });
        }
    });
})();
