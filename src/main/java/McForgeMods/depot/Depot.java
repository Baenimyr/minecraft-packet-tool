package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;

import java.util.*;

/**
 * Un dépôt est un ensemble de mod et leur versions. Il maintient une liste de mods connus et une liste de versions pour
 * ces mods. Un mod peut ne pas disposer de versions connues, cependant une version doit toujours disposer des
 * informations sur le mod.
 * <p>
 * Un exemple de dépot est le {@link DepotInstallation} qui représente les mods trouvés dans une installations
 * minecraft.
 */
public class Depot {
	protected final Map<String, Mod>          mods        = new HashMap<>();
	protected final Map<Mod, Set<ModVersion>> mod_version = new HashMap<>();
	
	/**
	 * Renvoit la liste complète, sans doublons, des mods présents dans le dépôt.
	 */
	public Collection<String> getModids() {
		return this.mods.keySet();
	}
	
	/**
	 * Renvoit le mod, et les informations qu'il contient, associé au modid.
	 */
	public Mod getMod(String modid) {
		return this.mods.get(modid);
	}
	
	/**
	 * Fournit la liste complète des version connues du mod. Si le mod n'est pas connu, renvoit {@code null}.
	 *
	 * @return un ensemble, ou {@code null}
	 */
	public Set<ModVersion> getModVersions(Mod mod) {
		return this.mod_version.get(mod);
	}
	
	/**
	 * Similaire à {@link #getModVersions(Mod)}.
	 */
	public Set<ModVersion> getModVersions(String modid) {
		return this.getModVersions(this.getMod(modid));
	}
	
	/**
	 * Cherche une version particulière d'un mod. Pour vérifier qu'une version est disponible, utiliser {@link
	 * #contains(ModVersion)}.
	 *
	 * @return {@link Optional<ModVersion>} si la version est disponible ou non.
	 */
	public Optional<ModVersion> getModVersion(Mod mod, Version version) {
		return this.contains(mod) ? this.getModVersions(mod).stream().filter(mv -> mv.version.equals(version)).findAny()
				: Optional.empty();
	}
	
	public boolean contains(String modid) {
		modid = modid.toLowerCase();
		return this.mods.containsKey(modid);
	}
	
	/**
	 * @return {@code true} si le mod demandé est connu de ce dépôt.
	 */
	public boolean contains(Mod mod) {
		return this.mods.containsValue(mod);
	}
	
	/**
	 * @return {@code true} si le mod à la version demandée est connu de ce dépôt.
	 */
	public boolean contains(ModVersion modVersion) {
		return this.contains(modVersion.mod) && this.mod_version.get(modVersion.mod).contains(modVersion);
	}
	
	/**
	 * Enregistre un nouveau mod dans le dépot.
	 *
	 * Si le mod existe déjà, les informations utiles sont importées.
	 * La mod est copié avant d'être ajouté au dépôt pour éviter les modifications partagées entres instances.
	 * Pour modifier les valeurs de l'instance enregistrée, il faut utiliser la valeur de retour de cette fonction ou
	 * {@link #getMod(String)}.
	 *
	 * @return l'instance réellement sauvegardée.
	 */
	public Mod ajoutMod(Mod mod) {
		if (this.mods.containsKey(mod.modid)) {
			Mod present = this.mods.get(mod.modid);
			present.fusion(mod);
			return present;
		} else {
			final Mod copie = mod.copy();
			this.mods.put(mod.modid, copie);
			this.mod_version.put(copie, new HashSet<>(2));
			return copie;
		}
	}
	
	/**
	 * Enregistre une nouvelle version d'un mod dans le dépot.
	 *
	 * De préférence, l'instance de mod utilisée à {@link
	 * ModVersion#mod} doit correspondre à celle renvoyée par {@link #ajoutMod(Mod)}.
	 * Il faut également que l'instance enregistrée ici ne correspondent pas à une instance enregistrée dans un autre
	 * dépôt afin d'éviter les modifications partagées entre dépôt.
	 */
	public ModVersion ajoutModVersion(final ModVersion modVersion) {
		Mod mod = this.ajoutMod(modVersion.mod);
		
		final Collection<ModVersion> liste = this.mod_version.get(mod);
		Optional<ModVersion> present = liste.stream().filter(m -> m.version.equals(modVersion.version)).findFirst();
		if (present.isPresent()) {
			present.get().fusion(modVersion);
			return present.get();
		} else {
			liste.add(modVersion);
			return modVersion;
		}
	}
	
	/**
	 * Recherche parmi les informations de ce dépot, une version compatible avec cet alias. En général, l'alias est un
	 * nom de fichier. Si cet alias contient le symbole '-', alors l'hypothèse est que le nom est de la forme
	 * <i>modid-version</i> et la recherche à lien en priorité sur les versions correspondants au <i>modid</i>.
	 *
	 * @return la première version trouvée, {@link Optional#empty()} sinon.
	 */
	public Optional<ModVersion> rechercheAlias(String nom) {
		int i = nom.indexOf('-');
		if (i > 0) {
			String test = nom.substring(0, i).toLowerCase();
			if (this.contains(test)) {
				for (ModVersion version : this.getModVersions(test)) {
					if (version.alias.contains(nom)) return Optional.of(version);
				}
			}
		}
		
		for (String modid : this.getModids()) {
			for (ModVersion version : this.getModVersions(modid))
				if (version.alias.contains(nom)) return Optional.of(version);
		}
		return Optional.empty();
	}
	
	/**
	 * Construit la liste des dépendances. Pour chaque mod présent dans la liste, extrait les dépendances et réalise
	 * l'intersection entre les intervalles de version. La fonction utilise les informations du dépot comme source
	 * d'informations pour les nouveaux mods rencontrés. Si un mod à étudier est inconnu, ses dépendances sont
	 * ignorées.
	 * <p>
	 * Si une dépendance porte sur un modid présent dans la liste à explorer, c'est la version en entrée qui est
	 * utilisée pour le reste de la résolution. Sinon l'algorithme fait l'hypothèse d'utiliser la version compatible
	 * la plus <b>récente</b>.
	 *
	 * @param liste: liste des mods pour lesquels chercher les dépendances.
	 * @return une map{modid -> version}
	 */
	public HashMap<String, VersionIntervalle> listeDependances(Collection<ModVersion> liste) {
		final HashMap<String, VersionIntervalle> requis = new HashMap<>();
		
		final LinkedList<ModVersion> temp = new LinkedList<>(liste);
		while (!temp.isEmpty()) {
			ModVersion nouveau = temp.removeFirst();
			Optional<ModVersion> local = this.getModVersion(nouveau.mod, nouveau.version);
			final ModVersion mver = local.orElse(nouveau);
			
			for (Map.Entry<String, VersionIntervalle> dependance : mver.requiredMods.entrySet()) {
				String modid_d = dependance.getKey();
				VersionIntervalle version_d = dependance.getValue();
				// Si un mod a déjà été rencontré, les intervalles sont fusionnées.
				if (requis.containsKey(modid_d)) requis.get(modid_d).intersection(version_d);
					// Si un mod possible est en attente, il servira à la résolution
				else if (temp.stream().map(mv -> mv.mod.modid).noneMatch(modid -> modid.equals(modid_d))) {
					requis.put(modid_d, version_d);
					
					if (this.contains(modid_d)) {
						Optional<ModVersion> candidat = this.getModVersions(modid_d).stream()
								.filter(modVersion -> modVersion.mcversion.equals(mver.mcversion))
								.filter(modVersion -> version_d.correspond(modVersion.version))
								.max(Comparator.comparing(m -> m.version));
						candidat.ifPresent(temp::add);
					}
				}
			}
		}
		return requis;
	}
	
	/**
	 * @return le nombre de versions connues par ce dépot.
	 */
	public int sizeModVersion() {
		return this.mod_version.values().stream().mapToInt(Set::size).sum();
	}
	
	public void clear() {
		this.mod_version.clear();
		this.mods.clear();
	}
}
