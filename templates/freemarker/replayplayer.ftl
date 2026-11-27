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
      <canvas data-rp="marks" style="width:100%;height:10px;display:block;"></canvas>
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
</div>
