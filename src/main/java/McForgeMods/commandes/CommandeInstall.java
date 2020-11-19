package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.solveur.SolveurPaquet;
import org.apache.commons.vfs2.*;
import org.json.JSONObject;
import org.json.JSONTokener;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
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
	
	static final ExecutorService executor = new ThreadPoolExecutor(0, 4, 1L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>());
	
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
		final DepotInstallation depotInstallation = new DepotInstallation(depotLocal, dossiers.minecraft);
		
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
			depotInstallation.statusImportation();
		} catch (IOException i) {
			System.err.println("[ERROR] Erreur de lecture du dépot !");
			return 1;
		}
		
		if (this.mcversion != null) {
			if (depotInstallation.mcversion == null)
				depotInstallation.mcversion = VersionIntervalle.read(this.mcversion);
			else if (!depotInstallation.mcversion.contains(Version.read(this.mcversion))) {
				System.err.println("[ERROR] Il est impossible de changer la version de minecraft ici.");
				return 1;
			}
		} else if (depotInstallation.mcversion == null) {
			System.err.println(
					"Aucune version de minecraft spécifiée. Veuillez compléter l'option -mc une première fois.");
			return 1;
		}
		
		
		// +-+-+-+-+-+-+-+- Solveur +-+-+-+-+-+-+-+-
		final SolveurPaquet solveur = new SolveurPaquet(depotLocal, depotInstallation.mcversion.minimum());
		solveur.ajoutVariable("forge", Collections.singleton(new Version(14, 23, 6)));
		depotInstallation.getModids().forEach(solveur::initialisationMod);
		
		for (final Map.Entry<String, VersionIntervalle> demande : demandes.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.err.printf("[ERROR] Modid inconnu: '%s'%n", demande.getKey());
				return ERREUR_MODID;
			}
			// Vérification que l'intervalle n'est pas trop large.
			if (demande.getValue().minimum() == null && demande.getValue().maximum() == null && (
					depotInstallation.mcversion.minimum() == null || depotInstallation.mcversion.maximum() == null)) {
				System.err.printf("[ERROR] Vous devez spécifier une version, pour le mod '%s' ou minecraft.%n",
						demande.getKey());
				return ERREUR_VERSION;
			}
			
			solveur.domaineVariable(demande.getKey()).removeIf(v -> !demande.getValue().contains(v));
		}
		
		// Ajout de toutes les installations manuelles dans l'installation
		// Les mods installés manuellement n'acceptent pas la version 'null'
		for (String modid : depotInstallation.getModids()) {
			DepotInstallation.Installation ins = depotInstallation.informations(modid);
			if ((ins.manuel() || ins.verrou()) && !demandes.containsKey(ins.paquet.modid)) {
				solveur.domaineVariable(ins.paquet.modid).removeIf(Objects::isNull);
			}
		}
		
		if (!solveur.resolution()) {
			System.err.println("Impossible de résoudre les dépendances:");
			//for (final String contrainte : solveur.listeContraintes()) {
			//	System.err.print(contrainte + "@" + solveur.contrainte(contrainte) + " ");
			//}
			System.err.println();
			return 10;
		}
		
		final List<PaquetMinecraft> installations = solveur.variables().stream()
				.filter(modid -> !modid.equals("forge") && !modid.equals("minecraft"))
				.filter(modid -> solveur.domaineVariable(modid).get(0) != null)
				.map(modid -> depotLocal.getModVersion(modid, solveur.domaineVariable(modid).get(0))).map(Optional::get)
				.filter(paquet -> !depotInstallation.contains(paquet.modid) || !depotInstallation
						.getInstallation(paquet.modid).version.equals(paquet.version)).collect(Collectors.toList());
		
		if (installations.size() != 0) {
			System.out.println("Installation des nouveaux mods:");
			StringJoiner joiner = new StringJoiner(" ");
			installations.forEach(mv -> joiner.add(mv.modid + "=" + mv.version));
			System.out.println("\t" + joiner.toString());
			
			if (!dry_run) {
				// Déclenche le téléchargement des mods
				Map<PaquetMinecraft, CompletableFuture<Void>> telechargements = new HashMap<>();
				for (PaquetMinecraft mversion : installations) {
					CompletableFuture<Void> t = CompletableFuture
							.supplyAsync(new TelechargementArchive(depotLocal, mversion), executor)
							.thenApplyAsync(url -> {
								if (url != null) {
									synchronized (depotInstallation) {
										try {
											if (!depotInstallation.suppressionConflits(mversion)) {
												System.err.println("[Install] [ERROR] " + mversion + " impossible de "
														+ "supprimer les versions en conflit.");
												return null;
											}
										} catch (FileSystemException fse) {
											System.err
													.println("[ERROR] suppression ancienne version: " + fse.getCode());
											return null;
										}
									}
								}
								return url;
							}).thenApplyAsync(new OuvertureArchive(depotInstallation)).thenAcceptAsync(succes -> {
								if (succes) {
									synchronized (depotInstallation) {
										depotInstallation.installation(mversion, demandes.containsKey(mversion.modid));
									}
								}
							});
					telechargements.put(mversion, t);
				}
				telechargements.forEach((mversion, cf) -> {
					try {
						cf.get();
					} catch (InterruptedException ignored) {
					} catch (ExecutionException erreur) {
						System.err.printf("Erreur téléchargement de '%s': %s%n", mversion, erreur.getMessage());
						erreur.printStackTrace();
					}
				});
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
	
	private static class TelechargementArchive implements Supplier<URI> {
		final DepotLocal      depotLocal;
		final PaquetMinecraft version;
		
		public TelechargementArchive(DepotLocal depot, PaquetMinecraft version) {
			this.depotLocal = depot;
			this.version = version;
		}
		
		@Override
		public URI get() {
			try {
				final FileSystemManager filesystem = VFS.getManager();
				final FileObject dossier_cache = filesystem
						.resolveFile(this.depotLocal.dossier.toUri().resolve("cache"));
				final FileObject source_dir = filesystem.resolveFile(depotLocal.dossier.toUri());
				final PaquetMinecraft.FichierMetadata archive_metadata = depotLocal.archives.get(version);
				
				FileObject dest = dossier_cache.resolveFile(version.modid + "-" + version.version.toString() + ".tar");
				if (!dest.exists()) {
					FileObject src = source_dir.resolveFile(archive_metadata.path);
					dest.copyFrom(src, new FileDepthSelector());
				}
				try (InputStream dest_is = dest.getContent().getInputStream()) {
					archive_metadata.checkSHA(dest_is);
				} catch (IOException e) {
					System.err.printf("[Install] [ERROR] impossible de vérifier l'intégrité de l'archive: %s%n",
							dest.getPublicURIString());
					return null;
				}
				
				return dest.getURL().toURI();
			} catch (FileSystemException | URISyntaxException io) {
				System.err.printf("[Install] impossible de télécharger l'archive pour %s%n", this.version);
				System.err.println("\t" + io.getClass() + ":" + io.getMessage());
				return null;
			}
		}
	}
	
	private static class OuvertureArchive implements Function<URI, Boolean> {
		final DepotInstallation depotInstallation;
		
		public OuvertureArchive(DepotInstallation depot) {
			this.depotInstallation = depot;
		}
		
		@Override
		public Boolean apply(URI archive_url) {
			if (archive_url == null) return false;
			
			try {
				FileSystemManager filesystem = VFS.getManager();
				// Dossier dans lequel sera ouvert l'archive
				final FileObject dossier_dest = filesystem.resolveFile(this.depotInstallation.dossier.toUri());
				
				FileObject archive_f = filesystem.resolveFile(archive_url);
				final FileObject archive_tar = filesystem.createFileSystem("tar", archive_f);
				FileObject mods = archive_tar.resolveFile(PaquetMinecraft.INFOS);
				
				InputStream is = mods.getContent().getInputStream();
				JSONObject json = new JSONObject(new JSONTokener(is));
				PaquetMinecraft modVersion = PaquetMinecraft.lecturePaquet(json);
				is.close();
				
				FileObject data = archive_tar.resolveFile(PaquetMinecraft.FICHIERS);
				for (final PaquetMinecraft.FichierMetadata metadata : modVersion.fichiers) {
					final FileObject src = data.resolveFile(metadata.path);
					final FileObject dest = dossier_dest.resolveFile(metadata.path);
					dest.copyFrom(src, new FileDepthSelector());
					
					try (InputStream fichier_contenu = dest.getContent().getInputStream()) {
						if (!metadata.checkSHA(fichier_contenu)) {
							System.err.printf("[ERROR] fichier %s non conforme.%n", dest.getName());
							return false;
						}
					}
					
					src.close();
					dest.close();
				}
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}
	}
}
