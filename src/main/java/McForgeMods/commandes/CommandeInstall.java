package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
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
		
		if (depotInstallation.mcversion == null) {
			System.err.println("Version de minecraft inconnu: utilisez 'set --minecraft VERSION'");
			return 1;
		}
		
		solveur.ajoutContrainte("minecraft", new VersionIntervalle(depotInstallation.mcversion));
		
		for (final Map.Entry<String, VersionIntervalle> demande : demandes.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.err.printf("[ERROR] Modid inconnu: '%s'%n", demande.getKey());
				return ERREUR_MODID;
			}
			// Vérification que l'intervalle n'est pas trop large.
			if (demande.getValue().minimum() == null && demande.getValue().maximum() == null && (
					depotInstallation.mcversion == null)) {
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
		
		final List<PaquetMinecraft> installations = solution.selection.stream().peek(System.out::println)
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
					final Superviseur I = new Superviseur(depotInstallation, depotLocal);
					if (I.installationListe(installations, (paquet) -> demandes.containsKey(paquet.modid))) {
						System.err.println("Echec de l'installation !");
						return 2;
					}
				} catch (FileSystemException e) {
					System.err.printf("Echec de l'installation, impossible d'accéder aux fichiers : %s%n",
							e.getMessage());
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
	
	/**
	 * Le superviseur contrôle le flux des opérations de modification de l'installation.
	 * <p>
	 * Chaque opération d'installation/suppression est divisée en tâches simples parallélisables.
	 */
	public static class Superviseur {
		private final DepotInstallation depotInstallation;
		private final DepotLocal        depotLocal;
		private final FileObject        dossier_cache;
		
		private final Map<PaquetMinecraft, CompletableFuture<Optional<URI>>> f_telechargement = new HashMap<>();
		private final Map<PaquetMinecraft, CompletableFuture<Boolean>>       f_suppression    = new HashMap<>();
		private final Map<PaquetMinecraft, CompletableFuture<Boolean>>       f_ouverture      = new HashMap<>();
		private final Map<PaquetMinecraft, CompletableFuture<Boolean>>       f_verification   = new HashMap<>();
		
		public Superviseur(final DepotInstallation depotInstallation, final DepotLocal depotLocal)
				throws FileSystemException {
			FileSystemManager fileSystem = VFS.getManager();
			this.depotInstallation = depotInstallation;
			this.depotLocal = depotLocal;
			this.dossier_cache = fileSystem.resolveFile(depotLocal.dossier.toUri());
		}
		
		/** Télécharge l'archive d'un paquet. */
		private CompletableFuture<Optional<URI>> telechargementPaquet(final PaquetMinecraft paquet) {
			if (!f_telechargement.containsKey(paquet)) {
				f_telechargement.put(paquet, CompletableFuture.supplyAsync(() -> {
					final Optional<URI> uri = depotInstallation
							.telechargementPaquet(dossier_cache, paquet, depotLocal.archives.get(paquet));
					if (uri.isPresent()) System.out.printf("%s téléchargé%n", paquet);
					else System.err.printf("Erreur téléchargement %s%n", paquet);
					return uri;
				}));
			}
			return f_telechargement.get(paquet);
		}
		
		/** Supprime un paquet */
		private CompletableFuture<Boolean> suppressionVersion(final PaquetMinecraft paquet) {
			if (!f_suppression.containsKey(paquet)) {
				final CompletableFuture<Boolean> suppression = CompletableFuture.supplyAsync(() -> {
					try {
						depotInstallation.desinstallation(paquet.modid);
						return true;
					} catch (FileSystemException fse) {
						return false;
					}
				});
				f_suppression.put(paquet, suppression);
			}
			return f_suppression.get(paquet);
		}
		
		/** Ouvre une archive et place les fichiers dans le répertoire d'installation. */
		private CompletableFuture<Boolean> ouverturePaquet(final PaquetMinecraft paquet) {
			if (!f_ouverture.containsKey(paquet)) {
				final Function<Optional<URI>, Boolean> ouverture = uri -> {
					if (uri.isPresent()) {
						try {
							depotInstallation.ouverturePaquet(uri.get());
							return true;
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					return false;
				};
				// Vérifie que les anciennes versions ont été supprimées avant d'ouvrir l'archive.
				// TODO: ajout attente de la confirmation de l'installation des dépendances.
				CompletableFuture<Boolean> suppression = CompletableFuture.completedFuture(true);
				if (depotInstallation.contains(paquet.modid)) {
					suppression = suppressionVersion(depotInstallation.getInstallation(paquet.modid))
							.thenCombineAsync(suppression, (suppr, c) -> suppr && c);
				}
				f_ouverture.put(paquet, suppression.thenCombineAsync(this.telechargementPaquet(paquet),
						(BiFunction<Boolean, Optional<URI>, Optional<URI>>) (s, uri) -> s ? uri : Optional.empty())
						.thenApplyAsync(ouverture));
			}
			return f_ouverture.get(paquet);
		}
		
		/** Vérifie que les fichiers extraits sont corrects. */
		private CompletableFuture<Boolean> verificationPaquet(final PaquetMinecraft paquet) {
			if (!f_verification.containsKey(paquet)) {
				f_verification.put(paquet, ouverturePaquet(paquet).thenApplyAsync((i) -> {
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
			}
			return f_verification.get(paquet);
		}
		
		/** Enregistre l'installation. */
		private CompletableFuture<Void> finalisation(final PaquetMinecraft paquet, boolean manuel) {
			return verificationPaquet(paquet).thenAcceptAsync((b) -> {
				depotInstallation.installation(paquet, manuel);
				System.out.printf("%s installé.%n", paquet);
			});
		}
		
		/**
		 * Installe une liste de paquet.
		 *
		 * @return {@code true} s'il y a un erreur durant l'installation.
		 */
		public boolean installationListe(final List<PaquetMinecraft> paquets, Predicate<PaquetMinecraft> manuel) {
			for (final PaquetMinecraft paquet : paquets) {
				try {
					this.finalisation(paquet, manuel.test(paquet)).get();
				} catch (Exception e) {
					e.printStackTrace();
					return true;
				}
			}
			return false;
		}
	}
}
