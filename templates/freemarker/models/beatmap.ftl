<div class="col-12  col-lg-<#if bigBm??>12<#else>6</#if> flex-column">

    <div class="osu-beatmap-card">
        <div class="osu-beatmap-container"
             role="link"
             tabindex="0"
             onclick="window.location.href='/b/${beatmap.id?c}';"
             onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); window.location.href='/b/${beatmap.id?c}'; }"
             style="cursor: pointer;">
            <!-- Beatmap Background -->
            <div class="osu-beatmap-bg" style="background-image: url('https://assets.ppy.sh/beatmaps/${beatmap.set_id?c}/covers/cover.jpg?1650681317')"></div>

            <!-- Content Overlay -->
            <div class="osu-beatmap-content">
                <!-- Status Badge -->
                <div class="osu-beatmap-status">
                    <span class="status-badge bg-${convertStatusBackColor(beatmap.status)}">
                        ${convertStatusBack(beatmap.status)}
                    </span>
                </div>

                <!-- Beatmap Info -->
                <div class="osu-beatmap-info">
                    <div class="osu-beatmap-title mt-2">
                        ${beatmap.artist} - ${beatmap.title}
                    </div>
                    <div class="osu-beatmap-creator">
                        mapped by ${beatmap.creator}
                    </div>

                    <div class="osu-beatmap-stats">
                        <div class="stat-item">
                            <i class="fa-solid fa-star"></i>
                            <span>${beatmap.diff?string("0.00")}</span>
                        </div>
                        <div class="stat-item">
                            <i class="fa-solid fa-play"></i>
                            <span>${beatmap.plays}</span>
                        </div>
                        <div class="stat-item">
                            <i class="fa-solid fa-circle-check"></i>
                            <span>${beatmap.passes}</span>
                        </div>
                        <#if beatmap.last_update??>
                        <div class="stat-item">
                            <i class="fa-solid fa-calendar"></i>
                            <span data-timestamp-format="date" data-timestamp="${beatmap.last_update}">
                                ${beatmap.last_update}
                            </span>
                        </div>
                        </#if>
                    </div>
                </div>

                <!-- Action Buttons -->
                <div class="osu-beatmap-actions">
                    <a download href="<#if (beatmap.set_id >= 1073741824)>https://c.taksiegra.ovh/d/${beatmap.set_id?c}<#else>https://catboy.best/d/${beatmap.set_id?c}</#if>"
                       class="osu-action-btn osu-download-btn"
                       title="Download Beatmap"
                       onclick="event.stopPropagation();"
                       onmouseover="this.style.background='rgba(46,204,113,0.3)'; this.style.borderColor='#2ecc71';"
                       onmouseout="this.style.background='rgba(255,255,255,0.1)'; this.style.borderColor='rgba(255,255,255,0.2)';">
                        <i class="fas fa-download"></i>
                    </a>
                </div>
            </div>
        </div>
    </div>

</div>
