package dev.osunolimits.models;
import lombok.Data;

@Data
public class UserBadge {
    private int id;
    private String image;
    private String caption;
    private String awardedDate;
    private String link;
}
