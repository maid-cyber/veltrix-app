; Veltrix VPN za Windows (Inno Setup)
#ifndef MyVersion
  #define MyVersion "1.0.0"
#endif
#define AppDir "..\build\compose\binaries\main\app\VeltrixVPN"

[Setup]
AppId={{6F1C7E2A-3B7D-4D9E-9A51-2C8F0B7E5A11}
AppName=Veltrix VPN
AppVersion={#MyVersion}
AppPublisher=Veltrix
DefaultDirName={autopf}\Veltrix VPN
DefaultGroupName=Veltrix VPN
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\build\installer
OutputBaseFilename=Veltrix-VPN-Setup-{#MyVersion}
SetupIconFile=veltrix.ico
UninstallDisplayIcon={app}\VeltrixVPN.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=force

[Languages]
Name: "bosnian"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Ikona na radnoj površini"; GroupDescription: "Dodatno:"

[Files]
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Veltrix VPN"; Filename: "{app}\VeltrixVPN.exe"
Name: "{autodesktop}\Veltrix VPN"; Filename: "{app}\VeltrixVPN.exe"; Tasks: desktopicon

[Registry]
; VPN adapter (Wintun) traži administratorska prava: program se uvijek pokreće kao administrator
Root: HKLM; Subkey: "SOFTWARE\Microsoft\Windows NT\CurrentVersion\AppCompatFlags\Layers"; ValueType: string; ValueName: "{app}\VeltrixVPN.exe"; ValueData: "~ RUNASADMIN"; Flags: uninsdeletevalue

[Run]
Filename: "{app}\VeltrixVPN.exe"; Description: "Pokreni Veltrix VPN"; Flags: nowait postinstall skipifsilent shellexec

[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Code]
procedure KillRunning();
var R: Integer;
begin
  Exec('taskkill.exe', '/F /IM sing-box.exe', '', SW_HIDE, ewWaitUntilTerminated, R);
  Exec('taskkill.exe', '/F /IM VeltrixVPN.exe', '', SW_HIDE, ewWaitUntilTerminated, R);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  KillRunning();
  Result := '';
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then KillRunning();
end;
