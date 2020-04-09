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
			
			assertTrue(tar.listeFichiers().contains("build.gradle"));
			EntreeTar build = tar.fichier("build.gradle");
			assertEquals(1688, build.taille);
			assertEquals(1579509932L, build.date);
			tar.lectureTotale();
			assertEquals(3, tar.size());
		} catch (Exception i) {
			i.printStackTrace();
			fail(i);
		}
	}
	
	@Test
	public void forgemods() {
		try (FileInputStream fichier = new FileInputStream(new File("Mods.tar"))) {
			FichierTar tar = new FichierTar(fichier);
			
			assertTrue(tar.contains("a/"));
			assertTrue(tar.contains("b/"));
			assertTrue(tar.contains("c/"));
			assertTrue(tar.contains("d/"));
			assertTrue(tar.contains("e/"));
			assertTrue(tar.contains("Mods.json"));
			
			tar.lectureTotale();
			assertEquals(112, tar.size());
			
			assertTrue(tar.listeFichiers().contains("t/thermalexpansion/thermalexpansion.json"));
			EntreeTar thermal = tar.fichier("t/thermalexpansion/thermalexpansion.json");
			assertEquals(690, thermal.taille);
		} catch (IOException io) {
			io.printStackTrace();
			fail(io);
		}
	}
}