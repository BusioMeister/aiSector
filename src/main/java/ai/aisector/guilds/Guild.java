package ai.aisector.guilds;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Guild {

    @EqualsAndHashCode.Include
    private final String tag;
    private String name;
    private UUID owner;
    private UUID deputy;

    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> mods = new HashSet<>();
    private final Set<UUID> invitations = new HashSet<>();
    private final Set<String> alliedGuilds = new HashSet<>();
    private final Set<String> allyInvitations = new HashSet<>();



    private boolean friendlyFireGuild = false;
    private boolean friendlyFireAllies = false;



    private String welcomeMessage;
    private String alertMessage;
    private String homeSector;   // nazwa sektora, np. "Sector1"
    private String homeWorld;// nazwa świata


    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;
    // UWAGA: ALBO ten konstruktor:
    public Guild(String tag, String name, UUID owner) {
        this.tag = tag;
        this.name = name;
        this.owner = owner;
    }

    // I NIC WIĘCEJ – usuń jakikolwiek drugi konstruktor z tym samym zestawem parametrów.
}