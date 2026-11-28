<#-- Replay player markup (osu!standard). Needs rpBase (URL that answers ?replay=1 and ?media=) and rpId.
     Script: /js/replay-player.js -->
<div class="rp-root" id="${rpId}" data-base="${rpBase}" style="display:none;">
  <div class="alert alert-danger small py-2" data-rp="error" style="display:none;">${t["analyzer.player_error"]}</div>
  <div style="position:relative;width:100%;max-width:900px;margin:0 auto;aspect-ratio:4/3;background:#0b0d12;border-radius:8px;overflow:hidden;">
    <canvas data-rp="canvas" tabindex="0" style="width:100%;height:100%;display:block;outline:none;"></canvas>
  </div>
  <div class="d-flex flex-wrap align-items-center gap-2 mt-3" style="max-width:900px;margin:0 auto;">
    <button type="button" class="btn btn-sm btn-secondary" data-rp="play" style="width:42px;"><i class="fa-solid fa-play"></i></button>
    <div class="flex-grow-1" style="min-width:160px;">
      <#-- the thumb of the seek bar (1.625rem in the theme) never goes past its own half, so the
           marks leave the same space at both ends and line up with where the thumb is -->
      <canvas data-rp="marks" style="width:calc(100% - 1.625rem);margin:0 0.8125rem;height:10px;display:block;"></canvas>
      <input type="range" class="form-range" data-rp="seek" min="0" max="1000" value="0" aria-label="${t["analyzer.chart_time"]}">
    </div>
    <span class="small text-muted font-monospace" data-rp="time">0:00 / 0:00</span>
    <select class="form-select form-select-sm" data-rp="speed" style="width:auto;">
      <option value="0.25">0.25x</option><option value="0.5">0.5x</option><option value="1" selected>1x</option><option value="1.5">1.5x</option><option value="2">2x</option>
    </select>
    <span class="small text-muted" title="${t["analyzer.player_volume"]}"><i class="fa-solid fa-volume-high"></i></span>
    <input type="range" class="form-range" data-rp="volume" min="0" max="1" step="0.05" value="0.4" style="width:90px;" title="${t["analyzer.player_volume"]}">
    <div class="form-check form-switch mb-0 small">
      <input class="form-check-input" type="checkbox" data-rp="trail" id="${rpId}-trail" checked>
      <label class="form-check-label" for="${rpId}-trail">${t["analyzer.player_trail"]}</label>
    </div>
    <div class="form-check form-switch mb-0 small">
      <input class="form-check-input" type="checkbox" data-rp="show300" id="${rpId}-300">
      <label class="form-check-label" for="${rpId}-300">${t["analyzer.player_300"]}</label>
    </div>
    <div class="d-flex align-items-center gap-1 small" data-rp-wrap="maniaskin" style="display:none !important;">
      <label class="text-muted" for="${rpId}-skin">${t["analyzer.player_mania_skin"]}</label>
      <select class="form-select form-select-sm" style="width:auto;" data-rp="maniaskin" id="${rpId}-skin">
        <option value="bars">${t["analyzer.player_skin_bars"]}</option>
        <option value="circles">${t["analyzer.player_skin_circles"]}</option>
      </select>
    </div>
    <div class="form-check form-switch mb-0 small">
      <input class="form-check-input" type="checkbox" data-rp="hitsounds" id="${rpId}-hs" checked>
      <label class="form-check-label" for="${rpId}-hs">${t["analyzer.player_hitsounds"]}</label>
    </div>
    <div class="form-check form-switch mb-0 small">
      <input class="form-check-input" type="checkbox" data-rp="followpoints" id="${rpId}-fp" checked>
      <label class="form-check-label" for="${rpId}-fp">${t["analyzer.player_followpoints"]}</label>
    </div>
    <div class="form-check form-switch mb-0 small" style="display:none;">
      <input class="form-check-input" type="checkbox" data-rp="hd" id="${rpId}-hd" checked>
      <label class="form-check-label" for="${rpId}-hd">${t["analyzer.player_hidden"]}</label>
    </div>
    <div class="form-check form-switch mb-0 small" style="display:none;">
      <input class="form-check-input" type="checkbox" data-rp="fl" id="${rpId}-fl" checked>
      <label class="form-check-label" for="${rpId}-fl">${t["analyzer.player_flashlight"]}</label>
    </div>
  </div>
  <div class="text-muted small mt-2 text-center"><span data-rp="noaudio" style="display:none;">${t["analyzer.player_no_audio"]} </span>${t["analyzer.player_keys"]}</div>
  <#if rpStaff?? && rpStaff>
  <#-- staff tools (anticheat): frame by frame, frame spacing, a second replay to compare -->
  <div class="border border-warning rounded p-2 mt-2 small" style="max-width:900px;margin:0 auto;">
    <div class="fw-semibold mb-1"><i class="fa-solid fa-shield-halved me-1"></i>${t["analyzer.rp_staff_tools"]}</div>
    <div class="text-muted mb-1">${t["analyzer.rp_step_hint"]}</div>
    <div class="font-monospace" data-rp="ac-info">-</div>
    <div class="text-muted mt-2">${t["analyzer.rp_ft_label"]}</div>
    <canvas data-rp="ac-ft" style="width:100%;height:56px;display:block;"></canvas>
    <div class="text-muted">${t["analyzer.rp_ft_how"]}</div>
    <div class="d-flex flex-wrap align-items-center gap-2 mt-2">
      <label class="text-muted" for="${rpId}-cmp">${t["analyzer.rp_cmp_label"]}</label>
      <input type="number" min="1" class="form-control form-control-sm" style="width:120px;" data-rp="ac-cmp-id" id="${rpId}-cmp">
      <button type="button" class="btn btn-sm btn-outline-warning" data-rp="ac-cmp-go">${t["analyzer.rp_cmp_go"]}</button>
      <span data-rp="ac-cmp-info" class="font-monospace"></span>
    </div>
    <span data-rp="ac-cmp-err" style="display:none;" class="text-danger">${t["analyzer.rp_cmp_err"]}</span>
  </div>
  </#if>
</div>
