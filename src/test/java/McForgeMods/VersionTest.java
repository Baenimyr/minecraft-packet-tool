package McForgeMods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionTest {
	
	@Test
	void read() {
		Version.VersionBuilder b1 = new Version.VersionBuilder("0.12-87");
		b1.read();
		Version v1 = b1.version();
		assertEquals(0, v1.get(0));
		assertEquals(12, v1.get(1));
		assertEquals(0, v1.get(2));
		assertEquals(1, v1.getRelease().size());
		assertEquals("87", v1.getRelease().get(0));
		assertEquals(0, v1.getBuild().size());
		
		Version.VersionBuilder b2 = new Version.VersionBuilder("1.12.2-alpha+484");
		b2.read();
		Version v2 = b2.version();
		assertEquals(1, v2.get(0));
		assertEquals(12, v2.get(1));
		assertEquals(2, v2.get(2));
		assertEquals(1, v2.getRelease().size());
		assertEquals("alpha", v2.getRelease().get(0));
		assertEquals(1, v2.getBuild().size());
		assertEquals("484", v2.getBuild().get(0));
	}
}