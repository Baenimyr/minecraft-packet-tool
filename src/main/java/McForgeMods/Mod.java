package McForgeMods;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Cette classe incarne un mod dans sa généralité.
 * Elle contient toutes les informations indépendantes des versions associées à un <i>modid</i>.
 * Ces informations se retrouveront sauvegardées dans le fichier <i>Mods.xz</i> en tête de dépôt.
 */
public class Mod {
    public final String modid;
    public String name;
    public String url = "";
    public String description = "";
    public String updateJSON = "";

    public Mod(String modid, String name) {
        this.modid = modid;
        this.name = name;
    }

    /** Importe un mod à partir des informations sauvegardées dans un JSON.
     * @throws JSONException en cas d'échec (absence de clé ou mauvais format).
     */
    public Mod(String modid, JSONObject json) throws JSONException {
        this.modid = modid.toLowerCase().intern();
        this.name = json.getString("name");
        if (json.has("url"))
            this.url = json.getString("url");
        if (json.has("description"))
            this.description = json.getString("description");
        if (json.has("updateJSON"))
            this.updateJSON = json.getString("updateJSON");
    }

    public void json(JSONObject json) {
        json.put("name", this.name);
        if (this.url.length() != 0)
            json.put("url", this.url);
        if (this.description.length() != 0)
            json.put("description", this.description);
        if (this.updateJSON.length() != 0)
            json.put("updateJSON", this.updateJSON);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mod mod = (Mod) o;
        return modid.equals(mod.modid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modid);
    }

    @Override
    public String toString() {
        return "Mod{" +
                "modid='" + modid + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
