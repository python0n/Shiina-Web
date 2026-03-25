<div class="list-group list-group-flush">
    <a href="/u/${u.id?c}<#if u.mode??>?mode=${u.mode}</#if>"
        class="list-group-item border rounded list-group-item-action d-flex align-items-center py-3">
        <div class="me-3">
            <img src="${avatarServer}/${u.id?c}?v=${.now?long?c}" alt="${u.name}" class="rounded" width="40" height="40">
        </div>

        <#if u.country??>

        <div class="me-3 d-none d-md-block">
            <img src="/img/flags/${u.country}.svg" alt="${u.country}" class="rounded" width="24" height="18">
        </div>

        </#if>

        <#if u.isOwner??>
            <div class="me-3">
                <i class="fa-solid fa-crown fa-2xl"></i>
            </div>
        </#if>

        <div class="flex-grow-1">
            <div class="fw-bold">${u.name}</div>

        </div>
        <div class="text-muted">
            <i class="fa fa-arrow-right"></i>
        </div>
    </a>
</div>