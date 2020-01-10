package McForgeMods;

import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

/** Cette classe incarne une version particulière d'un mod.
 * Elle rassemble toutes les informations relatives à une version, n'apparaissant pas dans {@link Mod}.
 * L'association à un fichier réel {@link #url} est optionnelle.
 *
 * @see Mod
 */
public class ModVersion {
    public final Mod mod;
    public final Version version;
    public final Version mcversion;

    String url = null;
    String parent = null;
    /**
     * Mods obligatoires pour le bon fonctionnement de celui-ci.
     */
    List<String> requiredMods;
    /**
     * Mods, si présents à charger avant.
     */
    List<String> dependants;

    /*
    List<String> authorList;
    String credits;
    String logoFile;
    List<String> screenshots;
     */

    public ModVersion(Mod mod, Version version, Version mcversion) {
        this.mod = mod;
        this.version = version;
        this.mcversion = mcversion;
    }

    /** Constructeur utilisé pour initialisé une version à partir des informations d'un dépot. */
    public ModVersion(Depot depot, JSONObject json) {
        String modid = json.getString("modid").toLowerCase();
        this.mod = depot.getMod(modid);
        this.version = Version.read(json.getString("version"));
        this.mcversion = Version.read(json.getString("mcversion"));

        url = json.has("url") ? json.getString("url") : null;
    }

    public void json(JSONObject json) {
        json.put("modid", this.mod.modid);
        json.put("version", this.version);
        json.put("mcversion", this.mcversion);

        if (this.url != null)
            json.put("url", this.url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModVersion that = (ModVersion) o;
        return mod.equals(that.mod) &&
                version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mod, version);
    }

    @Override
    public String toString() {
        return "ModVersion{" +
                "mod=" + mod.name +
                ", version=" + version +
                ", mcversion=" + mcversion +
                '}';
    }
}
