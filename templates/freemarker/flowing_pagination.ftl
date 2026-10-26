<nav class=" d-flex justify-content-center" aria-label="Page navigation">
    <ul class="pagination">
        <li class="page-item <#if page == 1>disabled</#if>">
            <a class="page-link" onclick="selectParam('page', '${page - 1}')" aria-label="${t["common.previous"]?html}">
                <span aria-hidden="true">
                    <i class="fa-solid fa-arrow-left"></i>
                </span>
            </a>
        </li>
        <#assign maxPagesBtn=0>
        <#assign maxPages=entries / 100>
        <#list page-3..maxPages + 1 as pages>
            <#if (pages> 0)>
            <#assign maxPagesBtn=maxPagesBtn+1>
                <#if maxPagesBtn==8>
                    <#break>
                </#if>

                <li class="page-item <#if page == pages>active</#if>"><a onclick="selectParam('page', '${pages}')" class="page-link">${pages}</a></li>
            </#if>
        </#list>
        <li class="page-item <#if (page == maxPages || entries == 0) || maxPages?ceiling == page>disabled</#if>">
            <a class="page-link" onclick="selectParam('page', '${page + 1}')" aria-label="${t["common.next"]?html}">
                <span aria-hidden="true">
                    <i class="fa-solid fa-arrow-right"></i>
                </span>
            </a>
        </li>
    </ul>
</nav>