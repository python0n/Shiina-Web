<#-- one mapset on the beatmap list (BeatmapList.MapSet) -->
<div class="col-12 col-lg-6 flex-column">
    <div class="osu-beatmap-card">
        <div class="osu-beatmap-container"
             role="link"
             tabindex="0"
             onclick="window.location.href='/b/${set.id?c}';"
             onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); window.location.href='/b/${set.id?c}'; }"
             style="cursor: pointer;">
            <div class="osu-beatmap-bg" style="background-image: url('<#if set.bss>https://c.taksiegra.ovh/api/v1/bss/bg/${set.set_id?c}<#else>https://assets.ppy.sh/beatmaps/${set.set_id?c}/covers/cover.jpg</#if>')"></div>

            <div class="osu-beatmap-content">
                <div class="osu-beatmap-status">
                    <span class="status-badge bg-${convertStatusBackColor(set.status)}">${convertStatusBack(set.status)}</span>
                    <#if set.bss><span class="status-badge shiina-bss-badge" title="${t["beatmaps.bss_title"]}">${t["beatmaps.bss_badge"]}</span></#if>
                </div>

                <div class="osu-beatmap-info">
                    <div class="osu-beatmap-title mt-2">${set.artist} - ${set.title}</div>
                    <div class="osu-beatmap-creator">${t["beatmap.mapped_by"]?replace("{creator}", set.creator)}</div>

                    <div class="shiina-diff-dots" aria-label="${set.diffs?size} ${t["beatmaps.difficulties"]}">
                        <#assign lastMode = -1>
                        <#list set.diffs as d>
                            <#if d.mode != lastMode>
                                <#assign lastMode = d.mode>
                                <img class="shiina-diff-dots__mode" alt="${["osu!", "osu!taiko", "osu!catch", "osu!mania"][d.mode]}" src="/img/modes/${["osu", "taiko", "catch", "mania"][d.mode]}.svg">
                            </#if>
                            <#if (set.diffs?size <= 14)>
                                <span class="shiina-diff-dots__dot" style="background:${d.colour}" title="${d.stars?string("0.00")}"></span>
                            </#if>
                        </#list>
                        <#if (set.diffs?size > 14)><span class="shiina-diff-dots__count">${set.diffs?size}</span></#if>
                    </div>

                    <div class="osu-beatmap-stats">
                        <div class="stat-item" title="${t["beatmaps.max_stars"]}"><i class="fa-solid fa-star"></i><span>${set.diff?string("0.00")}</span></div>
                        <div class="stat-item" title="${t["beatmaps.plays_here"]}"><i class="fa-solid fa-play"></i><span>${set.plays}</span></div>
                        <div class="stat-item"><i class="fa-solid fa-circle-check"></i><span>${set.passes}</span></div>
                        <#if set.last_update??>
                        <div class="stat-item"><i class="fa-solid fa-calendar"></i><span data-timestamp-format="date" data-timestamp="${set.last_update}">${set.last_update}</span></div>
                        </#if>
                    </div>
                </div>

                <div class="osu-beatmap-actions">
                    <#if !set.bss>
                    <button type="button" class="osu-action-btn shiina-preview-btn" data-preview="https://b.ppy.sh/preview/${set.set_id?c}.mp3"
                            title="${t["beatmaps.preview"]}" aria-label="${t["beatmaps.preview"]}"
                            onclick="event.stopPropagation(); shiinaPreview(this);">
                        <i class="fas fa-play"></i>
                    </button>
                    </#if>
                    <a download href="<#if set.bss>https://c.taksiegra.ovh/d/${set.set_id?c}<#else>https://osu.direct/api/d/${set.set_id?c}</#if>"
                       class="osu-action-btn osu-download-btn"
                       title="${t["beatmap.download"]}"
                       onclick="event.stopPropagation();">
                        <i class="fas fa-download"></i>
                    </a>
                </div>
            </div>
        </div>
    </div>
</div>
