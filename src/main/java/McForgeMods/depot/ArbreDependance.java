package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;

import java.util.*;

/**
 * L'arbre de dépendance montre comment les différents sont reliés entre eux. Chaque noeud représente un mod et une
 * intervalle de version valide, cependant un mod ne peut apparaitre qu'une seule fois dans l'arbre.
 */
public class ArbreDependance {
	final Map<Mod, VersionIntervalle> mods        = new HashMap<>();
	final Map<Mod, Set<Mod>>          dependances = new HashMap<>();
	
	/**
	 * Construit l'arbre de dépendance à partir des informations receuillis dans le dépôt. L'arbre contiendra les mods
	 * en entrées ainsi que toutes les dépendances nécessaires.
	 */
	public ArbreDependance(final Depot depot, Collection<ModVersion> mods) {
		this.ajoutDependanceRecursif(depot, mods);
	}
	
	/**
	 * Enregistre les mods comme sources de dépendances. Toutes les dépendances nécessaires sont calculés
	 * automatiquement en utilisant les informations de la dernière version disponibles correspondant aux contraintes.
	 */
	public void ajoutDependanceRecursif(final Depot depot, Collection<ModVersion> mods) {
		final LinkedList<ModVersion> temp = new LinkedList<>(mods);
		for (ModVersion version : mods) {
			this.mods.put(version.mod, new VersionIntervalle(version.version, version.version));
		}
		
		while (!temp.isEmpty()) {
			ModVersion nouveau = temp.removeFirst();
			final ModVersion local = depot.getModVersion(nouveau.mod, nouveau.version).orElse(nouveau);
			
			for (Map.Entry<String, VersionIntervalle> dependance : local.requiredMods.entrySet()) {
				String modid_d = dependance.getKey();
				VersionIntervalle version_d = dependance.getValue();
				
				Mod mod = null;
				if (depot.contains(modid_d)) mod = depot.getMod(modid_d);
				else {
					for (Mod mod1 : this.mods.keySet())
						if (mod1.modid.equals(modid_d)) {
							mod = mod1;
							break;
						}
				}
				if (mod == null) {
					mod = new Mod(modid_d, "?");
				}
				
				// Si ce mod n'a pas été traité, on cherche dans le dépôt une version satisfaisante qui fournirra
				// d'autres dépendances.
				if (!this.mods.containsKey(mod) && temp.stream().map(mv -> mv.mod.modid)
						.noneMatch(modid -> modid.equals(modid_d))) {
					if (depot.contains(modid_d)) {
						Optional<ModVersion> candidat = depot.getModVersions(modid_d).stream()
								.filter(modVersion -> modVersion.mcversion.equals(local.mcversion))
								.filter(modVersion -> version_d == VersionIntervalle.ouvert || version_d
										.correspond(modVersion.version)).max(Comparator.comparing(m -> m.version));
						candidat.ifPresent(temp::add);
					}
				}
				
				this.ajoutDependance(local.mod, mod, version_d);
			}
		}
	}
	
	/**
	 * Enregistre une nouvelle dépendance. Le mod requis est marqué comme nécessaire pour le mod parent. L'intervalle de
	 * version permit est réduit à l'intersection entre l'intervalle déjà présente en la nouvelle intervalle.
	 *
	 * @param parent: mod à l'origine de la demande
	 * @param requis: mod requis par le mod parent
	 * @param versions: intervalle de versions permettant de résoudre la dépendance.
	 * @return {@code true} si l'intervalle de version a changé.
	 */
	public boolean ajoutDependance(Mod parent, Mod requis, VersionIntervalle versions) {
		if (dependances.containsKey(parent)) dependances.get(parent).add(requis);
		else {
			Set<Mod> dep = new HashSet<>();
			dep.add(requis);
			dependances.put(parent, dep);
		}
		
		if (!mods.containsKey(parent)) mods.put(parent, VersionIntervalle.ouvert);
		if (!mods.containsKey(requis)) {
			mods.put(requis, versions);
			return true;
		} else if (mods.get(requis) == VersionIntervalle.ouvert) {
			mods.put(requis, versions);
			return versions != VersionIntervalle.ouvert;
		} else {
			final VersionIntervalle avant = new VersionIntervalle(mods.get(requis));
			mods.get(requis).intersection(versions);
			return !mods.get(requis).equals(avant);
		}
	}
	
	public Map<Mod, VersionIntervalle> requis() {
		return this.mods;
	}
	
	/** Isole les mods nécessaire pour les ancres.
	 * Tous les mods non choisis sont donc inutiles.
	 */
	public Set<Mod> sousgraphe(Set<Mod> ancres) {
		Set<Mod> selection = new HashSet<>();
		LinkedList<Mod> nouveaux = new LinkedList<>(ancres);
		
		while (!nouveaux.isEmpty()) {
			Mod mod = nouveaux.removeFirst();
			selection.add(mod);
			
			if (this.dependances.containsKey(mod))
				for (Mod dep : this.dependances.get(mod))
					if (!selection.contains(dep) && !nouveaux.contains(dep))
						nouveaux.add(dep);
		}
		
		return selection;
	}
}
