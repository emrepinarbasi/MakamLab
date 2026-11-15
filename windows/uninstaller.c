#include <windows.h>
#include <shlobj.h>
#include <wchar.h>

static int is_turkish_ui(void) {
    return PRIMARYLANGID(GetUserDefaultUILanguage()) == LANG_TURKISH;
}

static void parent_directory(wchar_t *path) {
    wchar_t *slash = wcsrchr(path, L'\\');
    if (slash != NULL) *slash = L'\0';
}

static void escape_powershell_literal(const wchar_t *source, wchar_t *target,
                                      size_t capacity) {
    size_t out = 0;
    for (size_t i = 0; source[i] != L'\0' && out + 2 < capacity; i++) {
        if (source[i] == L'\'') target[out++] = L'\'';
        target[out++] = source[i];
    }
    target[out] = L'\0';
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    (void) instance; (void) previous; (void) arguments; (void) show;
    const int turkish = is_turkish_ui();
    int answer = MessageBoxW(NULL,
        turkish ? L"MakamLab bu kullanıcı hesabından kaldırılsın mı?"
                : L"Remove MakamLab from this user account?",
        L"MakamLab", MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2 | MB_SETFOREGROUND);
    if (answer != IDYES) return 0;

    wchar_t selfPath[MAX_PATH * 4];
    wchar_t installDirectory[MAX_PATH * 4];
    wchar_t localAppData[MAX_PATH * 4];
    wchar_t expectedDirectory[MAX_PATH * 4];
    wchar_t tempDirectory[MAX_PATH * 4];
    wchar_t escapedInstall[MAX_PATH * 8];
    const wchar_t *failureMessage;
    wchar_t powershell[16384];
    if (!GetModuleFileNameW(NULL, selfPath,
            (DWORD)(sizeof(selfPath) / sizeof(selfPath[0])))) return 1;
    wcscpy_s(installDirectory,
             sizeof(installDirectory) / sizeof(installDirectory[0]), selfPath);
    parent_directory(installDirectory);
    if (FAILED(SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA, NULL,
                                SHGFP_TYPE_CURRENT, localAppData))) return 1;
    swprintf(expectedDirectory,
             sizeof(expectedDirectory) / sizeof(expectedDirectory[0]),
             L"%ls\\Programs\\MakamLab", localAppData);
    if (_wcsicmp(installDirectory, expectedDirectory) != 0) {
        MessageBoxW(NULL,
            turkish
                ? L"Bu kaldırıcı yalnız Windows'a kurulmuş MakamLab kopyası için kullanılabilir. Taşınabilir klasör ve içindeki diğer dosyalar silinmedi."
                : L"This uninstaller can only remove the installed MakamLab copy. The portable folder and its other files were not deleted.",
            L"MakamLab", MB_OK | MB_ICONERROR | MB_SETFOREGROUND);
        return 3;
    }
    if (!GetTempPathW((DWORD)(sizeof(tempDirectory) / sizeof(tempDirectory[0])),
                      tempDirectory)) return 1;
    escape_powershell_literal(installDirectory, escapedInstall,
                              sizeof(escapedInstall) / sizeof(escapedInstall[0]));
    failureMessage = turkish
        ? L"MakamLab tamamen kaldırılamadı. Uygulamayı kapatıp yeniden deneyin."
        : L"MakamLab could not be fully removed. Close the application and try again.";

    swprintf(powershell, sizeof(powershell) / sizeof(powershell[0]),
        L"powershell.exe -NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -Command \""
        L"$ErrorActionPreference='SilentlyContinue'; "
        L"Wait-Process -Id %lu; "
        L"$dest='%ls'; $menu=[Environment]::GetFolderPath('Programs'); "
        L"for($i=0;$i -lt 20 -and (Test-Path -LiteralPath $dest);$i++){ "
        L"Remove-Item -LiteralPath $dest -Recurse -Force; "
        L"if(Test-Path -LiteralPath $dest){Start-Sleep -Milliseconds 250} }; "
        L"if(Test-Path -LiteralPath $dest){ "
        L"$popup=New-Object -ComObject WScript.Shell; "
        L"$null=$popup.Popup('%ls',0,'MakamLab',16); exit 1 }; "
        L"Remove-Item -LiteralPath (Join-Path $menu 'MakamLab.lnk') -Force; "
        L"Remove-Item -LiteralPath 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\MakamLab' -Recurse -Force; exit 0\"",
        GetCurrentProcessId(), escapedInstall, failureMessage);

    STARTUPINFOW startup = {0};
    PROCESS_INFORMATION process = {0};
    startup.cb = sizeof(startup);
    BOOL launched = CreateProcessW(NULL, powershell, NULL, NULL, FALSE,
        CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT, NULL, tempDirectory,
        &startup, &process);
    if (!launched) {
        MessageBoxW(NULL,
            turkish ? L"Kaldırma işlemi başlatılamadı."
                    : L"The uninstaller could not be started.",
            L"MakamLab", MB_OK | MB_ICONERROR | MB_SETFOREGROUND);
        return 2;
    }
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    MessageBoxW(NULL,
        turkish ? L"MakamLab kaldırılıyor."
                : L"MakamLab is being removed.",
        L"MakamLab", MB_OK | MB_ICONINFORMATION | MB_SETFOREGROUND);
    return 0;
}
