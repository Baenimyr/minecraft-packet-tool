package McForgeMods;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Permet de décomposer et comparer les versions entre-elles.
 * Une version valide est composés de chiffres séparés par des virgules.
 * Le format minimum est <i>major.minor</i>. Le format le plus large est <i>major.medium.minor.patch</i>.
 *
 * @see <a href="https://semver.org">Semantic Versioning</a>
 * @since 2020-01-10
 */
public class Version implements Comparable<Version> {
    private static final Pattern version = Pattern.compile("^(?<major>\\p{Digit}+)\\.(?<medium>\\p{Digit}+)(\\.(?<minor>\\p{Digit}+)(\\.(?<patch>\\p{Digit}+))?)?");
    /** major, medium, minor, patch */
    private final int[] versions = new int[4];

    public Version(int major, int medium, int minor, int patch) {
        this.versions[0] = major;
        this.versions[1] = medium;
        this.versions[2] = minor;
        this.versions[3] = patch;
    }

    public Version(int major, int medium, int minor) {
        this(major, medium, minor, 0);
    }

    public static Version read(String version) throws IllegalArgumentException {
        final int[] versions = new int[]{0, 0, 0, 0};
        Matcher m = Version.version.matcher(version);
        if (m.find()) {
            versions[0] = Integer.parseInt(m.group("major"));
            versions[1] = Integer.parseInt(m.group("medium"));

            String minor = m.group("minor");
            String patch = m.group("patch");
            versions[2] = minor != null ? Integer.parseInt(minor) : 0;
            versions[3] = patch != null ? Integer.parseInt(patch) : 0;

            return new Version(versions[0], versions[1], versions[2], versions[3]);
        } else {
            throw new IllegalArgumentException(String.format("La version '%s' n'a pas de format reconnu !", version));
        }
    }

    @Override
    public int compareTo(Version version) {
        if (this.versions[0] == version.versions[0]) {
            if (this.versions[1] == version.versions[1]) {
                if (this.versions[2] == version.versions[2]) {
                    if (this.versions[3] == version.versions[3]) {
                        return 0;
                    } else return Integer.compare(this.versions[3], version.versions[3]);
                } else return Integer.compare(this.versions[2], version.versions[2]);
            } else return Integer.compare(this.versions[1], version.versions[1]);
        } else return Integer.compare(this.versions[0], version.versions[0]);
    }

    @Override
    public String toString() {
        return "Version{" + this.versions[0] + "." + this.versions[1] + "." + this.versions[2] + (this.versions[3] != 0 ? "." + this.versions[3] : "") + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(versions, ((Version) o).versions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(versions);
    }
}
