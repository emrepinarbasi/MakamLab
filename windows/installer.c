#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <stdint.h>
#include <string.h>
#include <wchar.h>

static const unsigned char TRAILER_MAGIC[8] = {'M','B','X','2','Z','I','P','!'};

static int is_turkish_ui(void) {
    return PRIMARYLANGID(GetUserDefaultUILanguage()) == LANG_TURKISH;
}

static void show_message(const wchar_t *turkish, const wchar_t *english,
                         UINT flags) {
    MessageBoxW(NULL, is_turkish_ui() ? turkish : english,
                L"MakamLab", MB_OK | flags | MB_SETFOREGROUND);
}

static int copy_payload(const wchar_t *selfPath, const wchar_t *zipPath) {
    HANDLE input = CreateFileW(selfPath, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
    if (input == INVALID_HANDLE_VALUE) return 0;
    LARGE_INTEGER size;
    if (!GetFileSizeEx(input, &size) || size.QuadPart < 16) { CloseHandle(input); return 0; }
    LARGE_INTEGER trailerPosition; trailerPosition.QuadPart = size.QuadPart - 16;
    if (!SetFilePointerEx(input, trailerPosition, NULL, FILE_BEGIN)) { CloseHandle(input); return 0; }
    uint64_t payloadLength = 0;
    unsigned char magic[8];
    DWORD read = 0;
    if (!ReadFile(input, &payloadLength, 8, &read, NULL) || read != 8
            || !ReadFile(input, magic, 8, &read, NULL) || read != 8
            || memcmp(magic, TRAILER_MAGIC, 8) != 0
            || payloadLength > (uint64_t)(size.QuadPart - 16)) {
        CloseHandle(input); return 0;
    }
    LARGE_INTEGER payloadPosition; payloadPosition.QuadPart = size.QuadPart - 16 - (LONGLONG)payloadLength;
    if (!SetFilePointerEx(input, payloadPosition, NULL, FILE_BEGIN)) { CloseHandle(input); return 0; }
    HANDLE output = CreateFileW(zipPath, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                                FILE_ATTRIBUTE_TEMPORARY, NULL);
    if (output == INVALID_HANDLE_VALUE) { CloseHandle(input); return 0; }
    unsigned char buffer[64 * 1024];
    uint64_t remaining = payloadLength;
    int ok = 1;
    while (remaining > 0) {
        DWORD requested = remaining > sizeof(buffer) ? sizeof(buffer) : (DWORD)remaining;
        DWORD got = 0, written = 0;
        if (!ReadFile(input, buffer, requested, &got, NULL) || got == 0
                || !WriteFile(output, buffer, got, &written, NULL) || written != got) {
            ok = 0; break;
        }
        remaining -= got;
    }
    CloseHandle(output); CloseHandle(input);
    return ok;
}

static int run_and_wait(wchar_t *commandLine) {
    STARTUPINFOW startup = {0};
    PROCESS_INFORMATION process = {0};
    startup.cb = sizeof(startup);
    if (!CreateProcessW(NULL, commandLine, NULL, NULL, FALSE,
                        CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
                        NULL, NULL, &startup, &process)) return 0;
    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exitCode = 1;
    GetExitCodeProcess(process.hProcess, &exitCode);
    CloseHandle(process.hThread); CloseHandle(process.hProcess);
    return exitCode == 0;
}

static void escape_powershell_literal(const wchar_t *source, wchar_t *target, size_t capacity) {
    size_t out = 0;
    for (size_t i = 0; source[i] != L'\0' && out + 2 < capacity; i++) {
        if (source[i] == L'\'') target[out++] = L'\'';
        target[out++] = source[i];
    }
    target[out] = L'\0';
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    (void) instance; (void) previous; (void) arguments; (void) show;
    wchar_t selfPath[MAX_PATH * 4];
    wchar_t tempDirectory[MAX_PATH * 4];
    wchar_t tempBase[MAX_PATH * 4];
    wchar_t zipPath[MAX_PATH * 4];
    wchar_t localAppData[MAX_PATH * 4];
    wchar_t installDirectory[MAX_PATH * 4];
    wchar_t appPath[MAX_PATH * 4];
    wchar_t uninstallPath[MAX_PATH * 4];
    wchar_t escapedZip[MAX_PATH * 8];
    wchar_t escapedInstall[MAX_PATH * 8];
    wchar_t escapedApp[MAX_PATH * 8];
    wchar_t escapedUninstall[MAX_PATH * 8];
    wchar_t powershell[32768];

    if (!GetModuleFileNameW(NULL, selfPath, (DWORD)(sizeof(selfPath) / sizeof(selfPath[0])))
            || !GetTempPathW((DWORD)(sizeof(tempDirectory) / sizeof(tempDirectory[0])), tempDirectory)
            || !GetTempFileNameW(tempDirectory, L"MBX", 0, tempBase)
            || FAILED(SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA | CSIDL_FLAG_CREATE,
                                       NULL, SHGFP_TYPE_CURRENT, localAppData))) {
        show_message(L"Kurulum klasörleri hazırlanamadı.",
                     L"The installation folders could not be prepared.",
                     MB_ICONERROR);
        return 1;
    }
    DeleteFileW(tempBase);
    swprintf(zipPath, sizeof(zipPath) / sizeof(zipPath[0]), L"%ls.zip", tempBase);
    if (!copy_payload(selfPath, zipPath)) {
        DeleteFileW(zipPath);
        show_message(L"Kurulum verisi okunamadı. Dosyayı yeniden indirin.",
                     L"The installer payload could not be read. Please download the file again.",
                     MB_ICONERROR);
        return 2;
    }

    swprintf(installDirectory, sizeof(installDirectory) / sizeof(installDirectory[0]),
             L"%ls\\Programs\\MakamLab", localAppData);
    swprintf(appPath, sizeof(appPath) / sizeof(appPath[0]),
             L"%ls\\MakamLab.exe", installDirectory);
    swprintf(uninstallPath, sizeof(uninstallPath) / sizeof(uninstallPath[0]),
             L"%ls\\Uninstall-MakamLab.exe", installDirectory);
    escape_powershell_literal(zipPath, escapedZip, sizeof(escapedZip) / sizeof(escapedZip[0]));
    escape_powershell_literal(installDirectory, escapedInstall, sizeof(escapedInstall) / sizeof(escapedInstall[0]));
    escape_powershell_literal(appPath, escapedApp, sizeof(escapedApp) / sizeof(escapedApp[0]));
    escape_powershell_literal(uninstallPath, escapedUninstall,
                              sizeof(escapedUninstall) / sizeof(escapedUninstall[0]));
    swprintf(powershell, sizeof(powershell) / sizeof(powershell[0]),
        L"powershell.exe -NoLogo -NoProfile -NonInteractive -Command \""
        L"$ErrorActionPreference='Stop'; "
        L"$zip='%ls'; $dest='%ls'; $app='%ls'; $uninstall='%ls'; "
        L"$stage=$dest+'.installing'; $backup=$dest+'.previous'; "
        L"if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }; "
        L"New-Item -ItemType Directory -Path $stage -Force | Out-Null; "
        L"Expand-Archive -LiteralPath $zip -DestinationPath $stage -Force; "
        L"$required=@('MakamLab.exe','Uninstall-MakamLab.exe','app\\Makam.jar','runtime\\bin\\javaw.exe','resources\\ffmpeg.exe'); "
        L"foreach($relative in $required) { if (-not (Test-Path -LiteralPath (Join-Path $stage $relative) -PathType Leaf)) { throw ('Missing installation file: '+$relative) } }; "
        L"$oldMoved=$false; "
        L"try { "
        L"if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Recurse -Force }; "
        L"if (Test-Path -LiteralPath $dest) { Move-Item -LiteralPath $dest -Destination $backup; $oldMoved=$true }; "
        L"Move-Item -LiteralPath $stage -Destination $dest; "
        L"} catch { "
        L"if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue }; "
        L"if ($oldMoved -and -not (Test-Path -LiteralPath $dest) -and (Test-Path -LiteralPath $backup)) { Move-Item -LiteralPath $backup -Destination $dest -ErrorAction SilentlyContinue }; "
        L"throw }; "
        L"$menu=[Environment]::GetFolderPath('Programs'); "
        L"$shell=New-Object -ComObject WScript.Shell; "
        L"$shortcut=$shell.CreateShortcut((Join-Path $menu 'MakamLab.lnk')); "
        L"$shortcut.TargetPath=$app; $shortcut.WorkingDirectory=$dest; $shortcut.Save(); "
        L"$key='HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\MakamLab'; "
        L"New-Item -Path $key -Force | Out-Null; "
        L"$quotedUninstall=[char]34+$uninstall+[char]34; "
        L"New-ItemProperty -Path $key -Name 'DisplayName' -PropertyType String -Value 'MakamLab' -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'DisplayVersion' -PropertyType String -Value '1.1.0' -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'Publisher' -PropertyType String -Value 'Prof. Dr. Emre PINARBAŞI' -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'DisplayIcon' -PropertyType String -Value $app -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'InstallLocation' -PropertyType String -Value $dest -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'UninstallString' -PropertyType String -Value $quotedUninstall -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'InstallDate' -PropertyType String -Value (Get-Date -Format 'yyyyMMdd') -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'NoModify' -PropertyType DWord -Value 1 -Force | Out-Null; "
        L"New-ItemProperty -Path $key -Name 'NoRepair' -PropertyType DWord -Value 1 -Force | Out-Null; "
        L"$bytes=(Get-ChildItem -LiteralPath $dest -Recurse -File | Measure-Object -Property Length -Sum).Sum; "
        L"New-ItemProperty -Path $key -Name 'EstimatedSize' -PropertyType DWord -Value ([Math]::Max(1,[int]($bytes/1KB))) -Force | Out-Null; "
        L"if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup -Recurse -Force -ErrorAction SilentlyContinue }\"",
        escapedZip, escapedInstall, escapedApp, escapedUninstall);

    int installed = run_and_wait(powershell);
    DeleteFileW(zipPath);
    if (!installed || GetFileAttributesW(appPath) == INVALID_FILE_ATTRIBUTES) {
        show_message(
            L"Kurulum tamamlanamadı. Kullanıcı klasörüne yazma iznini kontrol edin.",
            L"Installation failed. Check write access to your user folder.",
            MB_ICONERROR);
        return 3;
    }

    ShellExecuteW(NULL, L"open", appPath, NULL, installDirectory, SW_SHOWNORMAL);
    show_message(
        L"MakamLab kuruldu ve Başlat menüsüne eklendi.\n\nDesteklenen sistemler: Windows 10 ve Windows 11 (64 bit).",
        L"MakamLab was installed and added to the Start menu.\n\nSupported systems: Windows 10 and Windows 11 (64-bit).",
        MB_ICONINFORMATION);
    return 0;
}
