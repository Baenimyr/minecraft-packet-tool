package McForgeMods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Cette classe incarne une version particulière d'un mod.
 * Elle rassemble toutes les informations relatives à une version, n'apparaissant pas dans {@link Mod}.
 * L'association à un fichier réel {@link #urls} est optionnelle.
 *
 * @see Mod
 */
public class ModVersion {
    public final Mod mod;
    public final Version version;
    public final Version mcversion;

    /**
     * Liste des liens menant au fichier (localement ou un url)
     */
    public List<URL> urls = new ArrayList<>(1);
    String parent = null;
    /**
     * Mods obligatoires pour le bon fonctionnement de celui-ci.
     * Une intervalle de version doit être spécifiée.
     */
    public final Map<String, VersionIntervalle> requiredMods = new HashMap<>();
    /**
     * Mods, si présents à charger avant.
     * Aucune intervalle de version n'est nécessaire.
     */
    public final List<String> dependants = new ArrayList<>();

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

    /**
     * Constructeur utilisé pour initialisé une version à partir des informations d'un dépot.
     */
    public ModVersion(Mod mod, Version version, JSONObject json) {
        this.mod = mod;
        this.version = version;
        this.mcversion = Version.read(json.getString("mcversion"));

        if (json.has("urls")) {
            Object url = json.get("urls");
            if (url instanceof JSONArray) {
                for (int i = 0; i < ((JSONArray) url).length(); i++)
                    try {
                        this.urls.add(new URL(((JSONArray) url).getString(i)));
                    } catch (MalformedURLException u) {
                        u.printStackTrace();
                    }
            } else {
                try {
                    this.urls.add(new URL(json.getString("urls")));
                } catch (MalformedURLException u) {
                    u.printStackTrace();
                }
            }
        }
    }

    public void json(JSONObject json) {
        json.put("mcversion", this.mcversion);

        if (this.urls != null)
            json.put("urls", new JSONArray(this.urls));
        if (!this.requiredMods.isEmpty()) {
            JSONArray liste = new JSONArray();
            this.requiredMods.forEach((id, inter) -> liste.put(id + (inter.maximum != null && inter.minimum != null ? "@" + inter : "")));
            json.put("requiredMods", liste);
        }
        if (!this.dependants.isEmpty()) {
            JSONArray liste = new JSONArray(this.dependants);
            json.put("dependants", liste);
        }
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
