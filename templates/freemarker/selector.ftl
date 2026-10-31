<div class="my-2 my-lg-0 col col-auto">
    <img alt="osu!" src="/img/modes/osu.svg"
        class="sort-button <#if mode == 0 || mode == 999 || mode == 4 || mode == 8>selected</#if>"
        value="0" onclick="selectParam('mode', 0)"></img>
    <img alt="osu!taiko" src="/img/modes/taiko.svg" class="sort-button <#if mode == 1 || mode == 5>selected</#if>"
        value="1" onclick="selectParam('mode',1)"></img>
    <img alt="osu!catch" src="/img/modes/catch.svg" class="sort-button <#if mode == 2 || mode == 6>selected</#if>"
        value="2" onclick="selectParam('mode', 2)"></img>
    <img alt="osu!mania" src="/img/modes/mania.svg" class="sort-button <#if mode == 3>selected</#if>" value="3"
        onclick="selectParam('mode', 3)"></img>
</div>