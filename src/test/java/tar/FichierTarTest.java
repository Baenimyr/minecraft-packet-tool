package tar;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FichierTarTest {
	
	@Test
	public void build() {
		try (FileInputStream fichier = new FileInputStream(new File("build.tar"))) {
			FichierTar tar = null;
			try {
				 tar = new FichierTar(fichier);
			} catch (Exception i) {
				i.printStackTrace(System.err);
				fail();
			}
			
			assertEquals(3, tar.size());
			assertTrue(tar.listeFichiers().contains("build.gradle"));
			EntreeTar build = tar.fichier("build.gradle");
			assertEquals(1688, build.taille);
			assertEquals(1579509932L, build.date);
		} catch (Exception i) {
			i.printStackTrace();
			fail(i);
		}
	}
	
	@Test
	public void forgemods() {
		try (FileInputStream fichier = new FileInputStream(new File("forgemods.tar"))) {
			FichierTar tar = new FichierTar(fichier);
			
			assertEquals(112, tar.size());
			assertTrue(tar.listeFichiers().contains("a/"));
			assertTrue(tar.listeFichiers().contains("b/"));
			assertTrue(tar.listeFichiers().contains("c/"));
			assertTrue(tar.listeFichiers().contains("d/"));
			assertTrue(tar.listeFichiers().contains("e/"));
			assertTrue(tar.listeFichiers().contains("Mods.json"));
			
			assertTrue(tar.listeFichiers().contains("t/thermalexpansion/thermalexpansion.json"));
			EntreeTar thermal = tar.fichier("t/thermalexpansion/thermalexpansion.json");
			assertEquals(690, thermal.taille);
		} catch (IOException io) {
			io.printStackTrace();
			fail(io);
		}
	}
}