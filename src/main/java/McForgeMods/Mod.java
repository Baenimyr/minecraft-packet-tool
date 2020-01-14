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
    public final String name;
    public String url = null;
    public String description = null;
    public String updateJSON = null;

    public Mod(String modid, String name) {
        this.modid = modid.toLowerCase().intern();
        this.name = name;
    }

    /**
     * Importe un mod à partir des informations sauvegardées dans un JSON.
     *
     * @throws JSONException en cas d'échec (absence de clé ou mauvais format).
     */
    public Mod(String modid, JSONObject json) throws JSONException {
        this(modid, json.getString("name"));
        if (json.has("url")) {
            String url = json.getString("url");
            this.url = url.length() == 0 ? null : url;
        }
        if (json.has("description")) {
            String description = json.getString("description");
            this.description = description.length() == 0 ? null : description;
        }
        if (json.has("updateJSON")) {
            String updateJSON = json.getString("updateJSON");
            this.updateJSON = updateJSON.length() == 0 ? null : updateJSON;
        }
    }

    public void json(JSONObject json) {
        json.put("name", this.name);
        if (this.url != null) json.put("url", this.url);
        if (this.description != null) json.put("description", this.description);
        if (this.updateJSON != null) json.put("updateJSON", this.updateJSON);
    }

    /**
     * Récupère les informations utiles dans l'autre instance de mod.
     */
    public void fusion(Mod mod) {
        if (!this.modid.equals(mod.modid)) return;
        if (this.url == null) this.url = mod.url;
        if (this.description == null) this.description = mod.description;
        if (this.updateJSON == null) this.updateJSON = mod.updateJSON;
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
    
    public Mod copy() {
        Mod copie = new Mod(this.modid, this.name);
        copie.url = this.url;
        copie.description = this.description;
        copie.updateJSON = this.updateJSON;
        return copie;
    }
}
