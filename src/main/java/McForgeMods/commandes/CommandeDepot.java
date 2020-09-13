package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.ArchiveMod;
import McForgeMods.depot.DepotLocal;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * La fonction depot est responsable de toutes les interactions de modification du depot. Elle est le seule capable de
 * modifier les données.
 * <p>
 * Elle permet la lecture/ecriture des attributs des informations sauvegardées dans le dépot. La sous-commande
 * <i>import</i> associée avec une installation minecraft récupère les informations directement dans les
 * <i>mcmod.info</i> des fichiers jar trouvés.
 */
@CommandLine.Command(name = "depot", subcommands = {CommandeDepot.importation.class, CommandeUpdate.class},
		resourceBundle = "mcforgemods/lang/Depot")
public class CommandeDepot implements Runnable {
	
	@CommandLine.Option(names = {"--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	@CommandLine.Command(name = "refresh")
	public int refresh(@CommandLine.Option(names = "--depot") Path chemin_depot, @CommandLine.Mixin ForgeMods.Help help,
			@CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false", descriptionKey = "force")
					boolean force,
			@CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", descriptionKey = "verbose")
					boolean verbose) {
		DepotLocal depot = new DepotLocal(chemin_depot);
		
		try (final FileSystemManager filesystem = VFS.getManager()) {
			Queue<FileObject> fichiers = new LinkedList<>();
			fichiers.add(filesystem.resolveFile(depot.dossier.toUri()));
			
			while (!fichiers.isEmpty()) {
				FileObject f = fichiers.poll();
				if (f.isFolder()) {
					for (FileObject fc : f.getChildren())
						if (fc.getName().getBaseName().endsWith(".tar")) fichiers.add(fc);
				} else if (f.isFile() && !f.isHidden()) {
					FileObject archive = filesystem.createFileSystem("tar", f);
					FileObject data = archive.resolveFile(PaquetMinecraft.INFOS);
					if (data.exists()) {
						try (InputStream is = data.getContent().getInputStream()) {
							JSONObject json = new JSONObject(new JSONTokener(is));
							PaquetMinecraft paquet = PaquetMinecraft.lecturePaquet(json);
							depot.ajoutModVersion(paquet);
							depot.archives.put(paquet, new PaquetMinecraft.FichierMetadata(f.getName().getPath()));
							// System.out.println("[Archive] " + paquet);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		} catch (FileSystemException e) {
			e.printStackTrace();
		}
		
		System.out.printf("Dépot importé de %s: %d versions disponibles%n", depot.dossier, depot.sizeModVersion());
		
		if (verbose) {
			ArrayList<String> modids = new ArrayList<>(depot.getModids());
			modids.sort(String::compareTo);
			
			for (final String modid : modids) {
				for (final PaquetMinecraft version : depot.getModVersions(modid)) {
					ArbreDependance dependances = new ArbreDependance(depot, Collections.singleton(version));
					dependances.resolution();
					for (Map.Entry<String, VersionIntervalle> dep : dependances.requis().entrySet()) {
						if (!dep.getKey().equals("forge")) {
							if (!depot.contains(dep.getKey()) || depot.getModVersions(dep.getKey()).stream().noneMatch(
									v -> dep.getValue().equals(VersionIntervalle.ouvert()) || dep.getValue()
											.correspond(v.version))) {
								System.out
										.printf("'%s' a besoin de '%s@%s', mais il n'est pas disponible dans le dépôt !%n",
												version.toStringStandard(), dep.getKey(), dep.getValue());
							}
						}
					}
				}
			}
		}
		
		try {
			depot.sauvegarde();
		} catch (IOException i) {
			System.err.println("Erreur d'écriture du dépot: " + i.getClass().getSimpleName() + ": " + i.getMessage());
			return 1;
		}
		
		System.out.println("Dépot sauvegardé.");
		return 0;
	}
	
	@CommandLine.Command(name = "import")
	static class importation implements Callable<Integer> {
		@CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid", descriptionKey = "modids")
		List<String> modids;
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false")
		boolean      all;
		
		@CommandLine.Mixin
		ForgeMods.DossiersOptions dossiers;
		@CommandLine.Mixin
		ForgeMods.Help            help;
		
		public Integer call() {
			DepotLocal depot = new DepotLocal(dossiers.depot);
			try {
				depot.importation();
			} catch (IOException | JSONException | IllegalArgumentException i) {
				System.err.printf("Erreur de lecture des informations du dépot: %s %s%n", i.getClass().getSimpleName(),
						i.getMessage());
				return 1;
			}
			List<ArchiveMod> archives = ArchiveMod.analyseDossier(dossiers.minecraft);
			
			System.out.printf("%d mods chargés depuis '%s'.%n", archives.size(), dossiers.minecraft);
			
			final Collection<ArchiveMod> importation = new ArrayList<>();
			if (all) {
				importation.addAll(archives);
			} else if (modids != null && modids.size() > 0) {
				archives.stream().filter(a -> modids.contains(a.mod.modid)).forEach(importation::add);
				for (String modid : modids) {
					final File fichier = new File(modid);
					if (fichier.exists()) {
						try {
							ArchiveMod mod = ArchiveMod.importationJar(fichier);
							if (mod.isPresent()) {
								importation.add(mod);
							}
						} catch (IOException i) {
							i.printStackTrace();
						}
					} else if (archives.stream().noneMatch(a -> a.mod.modid.equals(modid))) {
						System.err.println("Mod non reconnu: '" + modid + "'");
					}
				}
			} else {
				System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
				return 2;
			}
			
			for (ArchiveMod version_client : importation) {
				final PaquetMinecraft.FichierMetadata fichierjar = new PaquetMinecraft.FichierMetadata(
						Path.of("mods").resolve(version_client.fichier.getName()));
				version_client.modVersion.fichiers.add(fichierjar);
				final Path archive_destination = depot.dossier.toAbsolutePath()
						.resolve(version_client.modVersion.toStringStandard() + ".tar");
				
				JSONObject json = new JSONObject();
				version_client.modVersion.ecriturePaquet(json);
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				try (OutputStreamWriter writer = new OutputStreamWriter(bytes)) {
					json.write(writer, 4, 4);
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
				
				try (FileOutputStream fos = new FileOutputStream(archive_destination.toFile());
					 TarArchiveOutputStream tar = new TarArchiveOutputStream(fos)) {
					TarArchiveEntry infos = new TarArchiveEntry(PaquetMinecraft.INFOS);
					infos.setSize(bytes.size());
					TarArchiveEntry mod = new TarArchiveEntry(version_client.fichier,
							PaquetMinecraft.FICHIERS + "mods/" + version_client.fichier.getName());
					
					tar.putArchiveEntry(infos);
					bytes.writeTo(tar);
					tar.closeArchiveEntry();
					tar.putArchiveEntry(mod);
					try (FileInputStream fichier_mod = new FileInputStream(version_client.fichier)) {
						fichier_mod.transferTo(tar);
					}
					tar.closeArchiveEntry();
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
				
				depot.ajoutModVersion(version_client.modVersion);
				depot.archives.put(version_client.modVersion,
						new PaquetMinecraft.FichierMetadata(depot.dossier.relativize(archive_destination)));
			}
			System.out.printf("%d versions importées.%n", importation.size());
			
			try {
				depot.sauvegarde();
			} catch (IOException i) {
				System.err.printf("Impossible de sauvegarder le dépot local à '%s': %s %s%n", depot.dossier,
						getClass().getSimpleName(), i.getMessage());
				return 1;
			}
			return 0;
		}
	}
}
