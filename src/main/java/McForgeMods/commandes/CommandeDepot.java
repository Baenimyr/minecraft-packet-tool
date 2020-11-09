package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.PaquetMinecraft;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArchiveMod;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.SolveurDependances;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.json.JSONException;
import org.json.JSONObject;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
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
					fichiers.addAll(Arrays.asList(f.getChildren()));
				} else if (f.isFile() && !f.isHidden() && f.getName().getBaseName().endsWith(".tar")) {
					FileObject archive = filesystem.createFileSystem("tar", f);
					FileObject data = archive.resolveFile(PaquetMinecraft.INFOS);
					if (data.exists()) {
						try (InputStream is = data.getContent().getInputStream()) {
							PaquetMinecraft paquet = PaquetMinecraft.lecturePaquet(is);
							PaquetMinecraft.FichierMetadata metadata = new PaquetMinecraft.FichierMetadata(
									f.getName().getPathDecoded());
							depot.ajoutModVersion(paquet);
							depot.archives.put(paquet, metadata);
							// System.out.println("[Archive] " + paquet);
						} catch (IOException e) {
							e.printStackTrace();
							return 1;
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
					SolveurDependances dependances = new SolveurDependances(depot);
					dependances.ajoutSelection(version);
					for (final String dep : dependances.listeContraintes()) {
						if (!dep.equals("forge") && !dep.equals("minecraft")) {
							final VersionIntervalle demande = dependances.contrainte(dep);
							if (!depot.contains(dep) || depot.getModVersions(dep).stream()
									.noneMatch(v -> demande.correspond(v.version))) {
								System.err
										.printf("'%s' a besoin de '%s@%s', mais il n'est pas disponible dans le dépôt !%n",
												version.toStringStandard(), dep, demande);
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
	
	private static Path pack(final PaquetMinecraft modVersion, Path dossier, Map<String, File> fichiers)
			throws IOException {
		final JSONObject json = new JSONObject();
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final Path archive_destination = dossier.resolve("" + modVersion.modid.charAt(0))
				.resolve(modVersion.toStringStandard() + ".tar");
		
		if (!archive_destination.getParent().toFile().exists() && !archive_destination.getParent().toFile().mkdirs()) {
			System.err.println("[ERROR] impossible de créer un dossier pour " + archive_destination);
			return null;
		}
		
		try (FileOutputStream fos = new FileOutputStream(archive_destination.toFile());
			 TarArchiveOutputStream tar = new TarArchiveOutputStream(fos)) {
			for (Map.Entry<String, File> fichier : fichiers.entrySet()) {
				try (FileInputStream fis = new FileInputStream(fichier.getValue())) {
					TarArchiveEntry mod = new TarArchiveEntry(fichier.getValue(),
							PaquetMinecraft.FICHIERS + fichier.getKey());
					MessageDigest digest = DigestUtils.getDigest(MessageDigestAlgorithms.SHA_256);
					
					tar.putArchiveEntry(mod);
					byte[] b = new byte[2048];
					int l;
					while ((l = fis.read(b)) != -1) {
						digest.update(b, 0, l);
						tar.write(b, 0, l);
					}
					tar.closeArchiveEntry();
					
					final String sha256 = Hex.encodeHexString(digest.digest());
					final PaquetMinecraft.FichierMetadata fichierjar = new PaquetMinecraft.FichierMetadata(
							fichier.getKey());
					fichierjar.SHA256 = sha256;
					modVersion.fichiers.add(fichierjar);
				}
			}
			
			try (OutputStreamWriter writer = new OutputStreamWriter(bytes)) {
				modVersion.ecriturePaquet(json);
				json.write(writer, 4, 4);
			}
			
			TarArchiveEntry infos = new TarArchiveEntry(PaquetMinecraft.INFOS);
			infos.setSize(bytes.size());
			tar.putArchiveEntry(infos);
			bytes.writeTo(tar);
			tar.closeArchiveEntry();
		}
		return archive_destination;
	}
	
	@CommandLine.Command(name = "import")
	static class importation implements Callable<Integer> {
		@CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid", descriptionKey = "modids")
		List<String> modids;
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false")
		boolean      all;
		
		@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
		Path depot_path = null;
		
		@CommandLine.Option(names = {"-f", "--from"}, description = "Dossier à parcourir", required = true)
		Path dossier_import = null;
		
		@CommandLine.Option(names = {"--ecraser"}, descriptionKey = "ecraser", defaultValue = "false")
		boolean ecraser = false;
		
		@CommandLine.Mixin
		ForgeMods.Help help;
		
		public Integer call() {
			DepotLocal depot = new DepotLocal(depot_path);
			try {
				depot.importation();
			} catch (IOException | JSONException | IllegalArgumentException i) {
				System.err.printf("Erreur de lecture des informations du dépot: %s %s%n", i.getClass().getSimpleName(),
						i.getMessage());
				return 1;
			}
			List<ArchiveMod> archives = ArchiveMod.analyseDossier(dossier_import);
			
			System.out.printf("%d mods chargés depuis '%s'.%n", archives.size(), dossier_import);
			
			final Collection<ArchiveMod> importation = new ArrayList<>();
			if (all) {
				importation.addAll(archives);
			} else if (modids != null && modids.size() > 0) {
				archives.stream().filter(a -> modids.contains(a.modVersion.modid)).forEach(importation::add);
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
					} else if (archives.stream().noneMatch(a -> a.modVersion.modid.equals(modid))) {
						System.err.println("Mod non reconnu: '" + modid + "'");
					}
				}
			} else {
				System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
				return 2;
			}
			
			if (!ecraser) {
				// Suppression des versions déjà présentes dans le dépôt qu'il ne faut pas écraser
				importation.removeIf(archive -> depot.contains(archive.modVersion));
			}
			
			for (ArchiveMod version_client : importation) {
				Path archive_destination = null;
				try {
					archive_destination = pack(version_client.modVersion, depot.dossier.toAbsolutePath(), Collections
							.singletonMap("mods/" + version_client.fichier.getName(), version_client.fichier));
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				if (archive_destination != null) {
					final PaquetMinecraft.FichierMetadata archive_metadata = new PaquetMinecraft.FichierMetadata(
							depot.dossier.relativize(archive_destination).toString());
					depot.ajoutModVersion(version_client.modVersion);
					depot.archives.put(version_client.modVersion, archive_metadata);
				}
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
