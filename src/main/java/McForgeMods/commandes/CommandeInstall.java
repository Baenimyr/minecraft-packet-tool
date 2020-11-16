package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.SolveurDependances;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * La commande <i>install</i> permet de récupérer les fichiers de mod présents sur le réseau. Pour l'installation d'un
 * nouveau mod, l'utilisateur peut spécifier la version de son choix. Elle sera interpretée comme un intervalle et la
 * version maximale possible sera installée. Si l'utilisateur spécifie une version de minecraft, spécifier la version du
 * mod devient facultatif et c'est la version maximale
 */
@CommandLine.Command(name = "install", sortOptions = false, resourceBundle = "mcforgemods/lang/Install")
public class CommandeInstall implements Callable<Integer> {
	static final int ERREUR_NOM     = 10;
	static final int ERREUR_MODID   = ERREUR_NOM + 1;
	static final int ERREUR_VERSION = ERREUR_NOM + 2;
	
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Parameters(arity = "0..n", descriptionKey = "mods")
	ArrayList<String> mods;
	
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	
	@CommandLine.Option(names = {"-mc", "--mcversion"})
	String mcversion;
	
	@CommandLine.Option(names = {"--no-dependencies"}, negatable = true, descriptionKey = "dependencies")
	boolean dependances = true;
	
	/*@CommandLine.Option(names = {"-y", "--yes"}, defaultValue = "false",
			description = "Répond automatiquement oui à toutes les questions.")
	boolean yes;*/
	
	@CommandLine.Option(names = {"--fix"}, defaultValue = "false")
	boolean fix;
	
	@CommandLine.Option(names = {"-s", "--simulate", "--dry-run"}, defaultValue = "false", descriptionKey = "simulate")
	boolean dry_run;
	
	@CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false",
			description = "Termine l'installation pour" + " tous les mods possibles")
	boolean force;
	
	
	@Override
	public Integer call() {
		final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		final DepotInstallation depotInstallation;
		/* Liste des mods à installer. */
		final SolveurDependances solveur = new SolveurDependances(depotLocal);
		
		/* Liste des installations explicitement demandées par l'utilisateur. */
		final Map<String, VersionIntervalle> demandes;
		try {
			if (this.mods != null) demandes = VersionIntervalle.lectureDependances(this.mods);
			else if (this.fix) demandes = new HashMap<>();
			else {
				System.err.println("Choississez un mod ou activer l'option --fix.");
				return 1;
			}
		} catch (IllegalArgumentException iae) {
			System.err.println("[ERROR] " + iae.getMessage());
			return 1;
		}
		
		try {
			depotLocal.importation();
			depotInstallation = DepotInstallation.depot(dossiers.minecraft);
			depotInstallation.statusImportation();
		} catch (IOException i) {
			System.err.println("[ERROR] Erreur de lecture du dépot !");
			return 1;
		}
		
		if (this.mcversion != null) {
			if (depotInstallation.mcversion == null)
				depotInstallation.mcversion = VersionIntervalle.read(this.mcversion);
			else if (!depotInstallation.mcversion.correspond(Version.read(this.mcversion))) {
				System.err.println("[ERROR] Il est impossible de changer la version de minecraft ici.");
				return 1;
			}
		} else if (depotInstallation.mcversion == null) {
			System.err.println(
					"Aucune version de minecraft spécifiée. Veuillez compléter l'option -mc une première fois.");
			return 1;
		}
		
		solveur.ajoutContrainte("minecraft", depotInstallation.mcversion);
		
		for (final Map.Entry<String, VersionIntervalle> demande : demandes.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.err.printf("[ERROR] Modid inconnu: '%s'%n", demande.getKey());
				return ERREUR_MODID;
			}
			// Vérification que l'intervalle n'est pas trop large.
			if (demande.getValue().minimum() == null && demande.getValue().maximum() == null && (
					depotInstallation.mcversion.minimum() == null || depotInstallation.mcversion.maximum() == null)) {
				System.err.printf("[ERROR] Vous devez spécifier une version, pour le mod '%s' ou " + "minecraft.%n",
						demande.getKey());
				return ERREUR_VERSION;
			}
			
			solveur.ajoutContrainte(demande.getKey(), demande.getValue());
		}
		
		// Ajout de toutes les installations manuelles dans l'installation
		for (String modid : depotInstallation.getModids()) {
			DepotInstallation.Installation ins = depotInstallation.informations(modid);
			if ((ins.manuel() || ins.verrou()) && !demandes.containsKey(ins.paquet.modid)) {
				solveur.ajoutSelection(ins.paquet);
			}
		}
		
		// Tente d'associer à chaque identifiant connu une version à installer.
		// Première tentative en conservant l'installation totale actuelle
		SolveurDependances solveur_minimal = new SolveurDependances(solveur);
		for (String modid : depotInstallation.getModids()) {
			final DepotInstallation.Installation ins = depotInstallation.informations(modid);
			if (!ins.manuel() && !ins.verrou() && !demandes.containsKey(ins.paquet.modid)) {
				solveur_minimal.ajoutSelection(ins.paquet);
			}
		}
		SolveurDependances solution_minimale = solveur_minimal.resolutionTotale();
		
		
		final SolveurDependances solution;
		if (solution_minimale != null) solution = solution_minimale;
			// En cas d'échec autorise la modification des versions non manuelles.
		else solution = solveur.resolutionTotale();
		
		if (solution == null) {
			System.err.println("Impossible de résoudre les dépendances:");
			for (final String contrainte : solveur.listeContraintes()) {
				System.err.print(contrainte + "@" + solveur.contrainte(contrainte) + " ");
			}
			System.err.println();
			return 10;
		}
		for (final String modid : solution.listeContraintes()) {
			if (modid.equalsIgnoreCase("forge") || modid.equalsIgnoreCase("minecraft")) continue;
			
			if (!depotLocal.contains(modid)) {
				System.err.printf("Modid requis inconnu: '%s'%n", modid);
				if (!this.force) return ERREUR_MODID;
			}
		}
		
		final List<PaquetMinecraft> installations = solution.selection.stream()
				.filter(s -> !depotInstallation.contains(s.modid) || !depotInstallation.getInstallation(s.modid).version
						.equals(s.version)).collect(Collectors.toList());
		if (installations.size() != 0) {
			System.out.println("Installation des nouveaux mods:");
			StringJoiner joiner = new StringJoiner(" ");
			installations.forEach(mv -> joiner.add(mv.modid + "=" + mv.version));
			System.out.println("\t" + joiner.toString());
			
			if (!dry_run) {
				// Déclenche le téléchargement des mods
				try {
					final Installation I = new Installation(depotInstallation, depotLocal, installations);
					I.manuels.addAll(demandes.keySet());
					if (!I.get()) {
						System.err.println("Echec de l'installation !");
						return 2;
					}
				} catch (Exception e) {
					System.err.println("Echec de l'installation !");
					return 2;
				}
			}
		} else {
			System.out.println("Pas de nouveaux mods à télécharger.");
			return 0;
		}
		
		try {
			depotInstallation.close();
		} catch (IOException e) {
			System.err.println("Impossible de sauvegarder la configuration de l'installation.");
			return 1;
		}
		return 0;
	}
	
	private static class Installation implements Supplier<Boolean> {
		private final DepotInstallation depotInstallation;
		private final DepotLocal        depotLocal;
		private final FileObject        dossier_cache;
		
		private final Map<PaquetMinecraft, CompletableFuture<Optional<URI>>> telechargements = new HashMap<>();
		private final Map<PaquetMinecraft, CompletableFuture<Boolean>>       ouverture       = new HashMap<>();
		private final Map<PaquetMinecraft, CompletableFuture<Boolean>>       verification    = new HashMap<>();
		private final List<String>                                           manuels         = new ArrayList<>();
		private final List<PaquetMinecraft>                                  installations;
		
		public Installation(final DepotInstallation depot, final DepotLocal depotLocal,
				final List<PaquetMinecraft> installation) throws FileSystemException {
			FileSystemManager fileSystem = VFS.getManager();
			this.depotInstallation = depot;
			this.depotLocal = depotLocal;
			this.installations = installation;
			this.dossier_cache = fileSystem.resolveFile(depotLocal.dossier.toUri());
		}
		
		private CompletableFuture<Optional<URI>> telechargementPaquet(final PaquetMinecraft paquet) {
			if (!telechargements.containsKey(paquet)) {
				telechargements.put(paquet, CompletableFuture.supplyAsync(() -> {
					final Optional<URI> uri = depotInstallation
							.telechargementPaquet(dossier_cache, paquet, depotLocal.archives.get(paquet));
					if (uri.isPresent()) System.out.printf("%s téléchargé%n", paquet);
					else System.err.printf("Erreur téléchargement %s%n", paquet);
					return uri;
				}));
			}
			return telechargements.get(paquet);
		}
		
		private CompletableFuture<Boolean> installationPaquet(final PaquetMinecraft paquet) {
			if (!ouverture.containsKey(paquet)) ouverture.put(paquet, telechargementPaquet(paquet).thenApply(uri -> {
				if (uri.isPresent()) {
					try {
						depotInstallation.ouverturePaquet(uri.get());
						return true;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			}));
			return ouverture.get(paquet);
		}
		
		private CompletableFuture<Boolean> verificationPaquet(final PaquetMinecraft paquet) {
			if (!verification.containsKey(paquet))
				verification.put(paquet, installationPaquet(paquet).thenApply((i) -> {
					if (i) {
						boolean verification = false;
						try {
							verification = depotInstallation.verificationIntegrite(paquet);
						} catch (FileSystemException e) {
							e.printStackTrace();
						}
						if (!verification) System.err.printf("Echec vérification du paquet %s%n", paquet);
						return verification;
					}
					return false;
				}));
			return verification.get(paquet);
		}
		
		private CompletableFuture<Void> finalisation(final PaquetMinecraft paquet) {
			return verificationPaquet(paquet).thenAccept((b) -> {
				depotInstallation.installation(paquet, manuels.contains(paquet.modid));
				System.out.printf("%s installé.%n", paquet);
			});
		}
		
		@Override
		public Boolean get() {
			boolean succes = true;
			for (final PaquetMinecraft paquet : installations) {
				try {
					finalisation(paquet).get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					succes = false;
					System.err.printf("Impossible d'installer %s%n", paquet);
				}
			}
			return succes;
		}
	}
}
