package McForgeMods.solveur;

import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.Depot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Solveur de dépendance pour les paquets minecraft.
 * <p>
 * Les mods doivent être initialisés par {@link #initialisationMod(String)} qui permet d'enregistrer la variable et de
 * générer les contraintes de dépendances. Chaque version disponible dans le dépôt est ajoutée au domaine de la
 * variable, la valeur {@code null} correspond à un mod non installé. Tous mods utilisé pour la déclaration de
 * contrainte doit être chargé dans le solveur.
 */
public class SolveurPaquet extends Solveur<String, Version> {
	
	final Depot depot;
	
	public SolveurPaquet(Depot info, Version minecraft, Version forge) {
		this.depot = info;
		this.ajoutVariable("minecraft", Collections.singleton(minecraft));
		this.ajoutVariable("forge", Collections.singleton(forge));
	}
	
	/**
	 * Initialise le domaine et les contraintes sortante d'un mod.
	 */
	public synchronized void initialisationMod(final String modid) {
		if (!domaines.containsKey(modid)) {
			HashSet<Version> versions = depot.getModVersions(modid).stream().map(p -> p.version)
					.collect(Collectors.toCollection(HashSet::new));
			versions.add(null);
			this.ajoutVariable(modid, versions);
			this.marquerVariable(modid);
			
			for (final PaquetMinecraft paquet : depot.getModVersions(modid)) {
				for (Map.Entry<String, VersionIntervalle> dep : paquet.requiredMods.entrySet()) {
					final ContrainteDependance dependance = new ContrainteDependance(modid, paquet.version,
							dep.getKey(), dep.getValue());
					
					this.initialisationMod(dependance.modid_dep);
					this.ajoutContrainte(dependance);
					this.marquerVariable(dep.getKey());
				}
				
				this.ajoutContrainte(new ContrainteDependance(modid, paquet.version, "minecraft", paquet.mcversion));
			}
		}
	}
	
	@Override
	public Domaine<Version> domaineVariable(final String id) {
		if (!domaines.containsKey(id)) this.initialisationMod(id);
		return super.domaineVariable(id);
	}
	
	// -+-+-+-+-+-+-+-+-+-+
	
	public void init(Map<String, VersionIntervalle> demandes) {
		for (final String modid : demandes.keySet()) {
			this.initialisationMod(modid);
			final VersionIntervalle intervalle = demandes.get(modid);
			this.domaineVariable(modid).removeIf(version -> !intervalle.correspond(version));
			this.domaineVariable(modid).push();
		}
		this.coherence();
	}
}
