#include <windows.h>
#include <wchar.h>

static void parent_directory(wchar_t *path) {
    wchar_t *slash = wcsrchr(path, L'\\');
    if (slash != NULL) *slash = L'\0';
}

static int is_turkish_ui(void) {
    return PRIMARYLANGID(GetUserDefaultUILanguage()) == LANG_TURKISH;
}

static void show_error(const wchar_t *turkish, const wchar_t *english) {
    MessageBoxW(NULL, is_turkish_ui() ? turkish : english, L"MakamLab",
                MB_OK | MB_ICONERROR | MB_SETFOREGROUND);
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    (void) instance;
    (void) previous;
    (void) arguments;
    (void) show;

    wchar_t base[MAX_PATH * 4];
    DWORD length = GetModuleFileNameW(NULL, base,
        (DWORD)(sizeof(base) / sizeof(base[0])));
    if (length == 0 || length >= sizeof(base) / sizeof(base[0])) {
        show_error(L"Uygulama konumu okunamadı.",
                   L"The application location could not be read.");
        return 1;
    }
    parent_directory(base);

    wchar_t java_path[MAX_PATH * 4];
    wchar_t class_path[MAX_PATH * 8];
    wchar_t resource_path[MAX_PATH * 4];
    wchar_t command_line[32768];
    swprintf(java_path, sizeof(java_path) / sizeof(java_path[0]),
             L"%ls\\runtime\\bin\\javaw.exe", base);
    swprintf(class_path, sizeof(class_path) / sizeof(class_path[0]),
             L"%ls\\app\\*", base);
    swprintf(resource_path, sizeof(resource_path) / sizeof(resource_path[0]),
             L"%ls\\resources", base);

    if (GetFileAttributesW(java_path) == INVALID_FILE_ATTRIBUTES) {
        show_error(
            L"MakamLab'ın Java çalışma ortamı bulunamadı. Kurulumu yeniden çalıştırın.",
            L"MakamLab's Java runtime is missing. Please run the installer again.");
        return 2;
    }

    swprintf(command_line, sizeof(command_line) / sizeof(command_line[0]),
        L"\"%ls\" -Dfile.encoding=UTF-8 -Xmx2g "
        L"-Dmakambox.resources=\"%ls\" -cp \"%ls\" "
        L"com.makambox.studio.Makam",
        java_path, resource_path, class_path);

    STARTUPINFOW startup = {0};
    PROCESS_INFORMATION process = {0};
    startup.cb = sizeof(startup);
    if (!CreateProcessW(java_path, command_line, NULL, NULL, FALSE,
                        CREATE_UNICODE_ENVIRONMENT, NULL, base,
                        &startup, &process)) {
        wchar_t tr_message[512];
        wchar_t en_message[512];
        DWORD error_code = GetLastError();
        swprintf(tr_message, sizeof(tr_message) / sizeof(tr_message[0]),
                 L"MakamLab başlatılamadı (Windows hata kodu: %lu).", error_code);
        swprintf(en_message, sizeof(en_message) / sizeof(en_message[0]),
                 L"MakamLab could not be started (Windows error code: %lu).", error_code);
        show_error(tr_message, en_message);
        return 3;
    }

    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return 0;
}
