#include <mach-o/dyld.h>
#include <errno.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#if defined(__aarch64__)
#define MAKAM_ARCH "arm64"
#elif defined(__x86_64__)
#define MAKAM_ARCH "x86_64"
#else
#error Unsupported MakamLab architecture
#endif

int main(void) {
    char executable[PATH_MAX];
    uint32_t size = sizeof(executable);
    if (_NSGetExecutablePath(executable, &size) != 0) return 1;

    char contents[PATH_MAX];
    if (realpath(executable, contents) == NULL) return 1;
    char *name = strrchr(contents, '/');
    if (name == NULL) return 1;
    *name = '\0';
    char *macos = strrchr(contents, '/');
    if (macos == NULL) return 1;
    *macos = '\0';

    char java[PATH_MAX];
    char classpath[PATH_MAX];
    char icon[PATH_MAX + 32];
    if (snprintf(java, sizeof(java), "%s/runtime-%s/bin/java", contents, MAKAM_ARCH) >= (int) sizeof(java)
            || snprintf(classpath, sizeof(classpath), "%s/app/*", contents) >= (int) sizeof(classpath)
            || snprintf(icon, sizeof(icon), "-Xdock:icon=%s/Resources/MakamLab.icns", contents) >= (int) sizeof(icon)) {
        return 1;
    }

    execl(java, java, "-Dfile.encoding=UTF-8", "-Xmx2g",
            "--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
            "-Xdock:name=MakamLab", icon, "-cp", classpath, "com.makambox.studio.Makam",
            (char *) NULL);
    perror("MakamLab Java runtime");
    return errno == ENOENT ? 127 : 1;
}
