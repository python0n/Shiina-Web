package dev.osunolimits.api;

import java.io.IOException;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import dev.osunolimits.common.APIRequest;
import dev.osunolimits.models.Beatmap;
import lombok.Data;
import okhttp3.Request;
import okhttp3.Response;

public class BeatmapQuery extends APIQuery {

    @Data
    public class BeatmapResponse {
        private String status;
        private Beatmap[] data;
        private BeatmapMeta meta;
    }

    @Data
    public class BeatmapMeta {
        private int total;
        private int page;
        private int page_size;
    }

    public BeatmapQuery() {
        super("beatmaps", 30, 25);
    }

    public BeatmapResponse getBeatmaps(int page, int pageSize, int status, Optional<String> artist,
            Optional<String> creator, int mode) {
        String url = "/v2/maps";
        url += getParameter() + "page=" + page;
        url += getParameter() + "page_size=" + pageSize;
        url += getParameter() + "group_by_set=true";

        if (status != 999) {
            url += getParameter() + "status=" + status;
        }

        if (artist.isPresent()) {
            url += getParameter() + "artist=" + artist.get();
        }

        if (creator.isPresent()) {
            url += getParameter() + "creator=" + creator.get();
        }

        if (mode != 999) {
            url += getParameter() + "mode=" + mode;
        }

        Request request = APIRequest.build(url);

        Response response;
        JsonElement element;
        BeatmapResponse beatmapResponse;

        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            logger.error("Error fetching beatmaps from bancho.py API");
            return null;
        }

        try {
            element = JsonParser.parseString(response.body().string());
        } catch (JsonSyntaxException | IOException e) {
            logger.error("Invalid JSON response from bancho.py API");
            return null;
        }

        try {
            beatmapResponse = new Gson().fromJson(element, BeatmapResponse.class);
        } catch (Exception e) {
            logger.error("Error parsing JSON response from bancho.py API");
            return null;
        }

        return beatmapResponse;
    }
}
