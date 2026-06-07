#define MyAppName "Localization Editor SC2 KSP"
#define MyAppPublisher "Lenc"
#define MyAppExeName "Localization Editor SC2 KSP.exe"

#ifndef MyAppVersion
  #define MyAppVersion "2.2"
#endif

#ifndef MyDisplayVersion
  #define MyDisplayVersion "2.2"
#endif

#ifndef MyAppExeDir
  #define MyAppExeDir "..\\target\\installer-app-image\\Localization Editor SC2 KSP"
#endif

#ifndef MyWizardSmallBmp
  #define MyWizardSmallBmp "..\\installer\\generated\\wizard-small.bmp"
#endif

#ifndef MyWizardLargeBmp
  #define MyWizardLargeBmp "..\\installer\\generated\\wizard-large.bmp"
#endif

#ifndef MyOutputDir
  #define MyOutputDir "..\\dist\\beautiful-installer"
#endif

[Setup]
AppId={{A3E8A3DF-9D49-45B9-9149-C6B1E5299AC2}
AppName={#MyAppName}
AppVerName={#MyAppName} {#MyDisplayVersion}
AppVersion={#MyDisplayVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP
AppSupportURL=https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/issues
AppUpdatesURL=https://github.com/VoVanRusLvSC2/Localization-Editor-SC2-KSP/releases
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
DisableProgramGroupPage=yes
DisableReadyMemo=no
DisableDirPage=no
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
WizardResizable=no
WizardImageStretch=no
WizardImageBackColor=$07110F
WizardSmallImageBackColor=$07110F
SetupIconFile=..\src\main\resources\Assets\Textures\Icon.ico
WizardImageFile={#MyWizardLargeBmp}
WizardSmallImageFile={#MyWizardSmallBmp}
OutputDir={#MyOutputDir}
OutputBaseFilename=Localization-Editor-SC2-KSP-{#MyDisplayVersion}-setup
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersion}
VersionInfoTextVersion={#MyDisplayVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=Localization Editor SC2 KSP Installer
VersionInfoProductName={#MyAppName}
VersionInfoProductTextVersion={#MyDisplayVersion}
SetupLogging=yes
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog
UsePreviousAppDir=yes
UsePreviousTasks=yes
DisableWelcomePage=no
UninstallDisplayName={#MyAppName}
ChangesAssociations=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Dirs]
Name: "{app}\glossary"; Permissions: users-modify

[Files]
Source: "{#MyAppExeDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "glossary\*"
Source: "..\src\main\resources\glossary\sc2_word_glossary_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist uninsneveruninstall
Source: "..\src\main\resources\glossary\Addition_UnitNames_Detailed_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist uninsneveruninstall
Source: "..\src\main\resources\glossary\Addition_Weapons_Detailed_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist uninsneveruninstall
Source: "..\src\main\resources\glossary\Addition_Abilities_Detailed_KSP.txt"; DestDir: "{app}\glossary"; Flags: ignoreversion onlyifdoesntexist uninsneveruninstall

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
var
  CleanupPage: TWizardPage;
  RemovePreviousCheckBox: TNewCheckBox;
  RemovePreviousDescription: TNewStaticText;
  GlossaryBackupDir: String;

function UninstallRegistryKey(): String;
begin
  Result := 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{#SetupSetting("AppId")}_is1';
end;

function ExtractExePath(Value: String): String;
var
  EndQuotePos: Integer;
  ExePos: Integer;
begin
  Result := Trim(Value);
  if Result = '' then
    exit;

  if Result[1] = '"' then
  begin
    EndQuotePos := Pos('"', Copy(Result, 2, Length(Result) - 1));
    if EndQuotePos > 0 then
    begin
      Result := Copy(Result, 2, EndQuotePos - 1);
      exit;
    end;
  end;

  ExePos := Pos('.exe', Lowercase(Result));
  if ExePos > 0 then
    Result := Copy(Result, 1, ExePos + 3);
end;

function GetPreviousUninstaller(var Uninstaller: String): Boolean;
var
  Key: String;
begin
  Result := False;
  Uninstaller := '';
  Key := UninstallRegistryKey();

  if RegQueryStringValue(HKLM, Key, 'QuietUninstallString', Uninstaller) or
     RegQueryStringValue(HKCU, Key, 'QuietUninstallString', Uninstaller) then
  begin
    Uninstaller := ExtractExePath(Uninstaller);
    Result := FileExists(Uninstaller);
    exit;
  end;

  if RegQueryStringValue(HKLM, Key, 'UninstallString', Uninstaller) or
     RegQueryStringValue(HKCU, Key, 'UninstallString', Uninstaller) then
  begin
    Uninstaller := ExtractExePath(Uninstaller);
    Result := FileExists(Uninstaller);
  end;
end;

procedure BackupEditableGlossary();
var
  SourceDir: String;
begin
  SourceDir := ExpandConstant('{app}\glossary');
  GlossaryBackupDir := ExpandConstant('{tmp}\le-glossary-backup');

  if DirExists(GlossaryBackupDir) then
    DelTree(GlossaryBackupDir, True, True, True);

  if not DirExists(SourceDir) then
    exit;

  if RenameFile(SourceDir, GlossaryBackupDir) then
    Log('Editable glossary folder was backed up before uninstall: ' + GlossaryBackupDir)
  else
    Log('Could not back up editable glossary folder before uninstall: ' + SourceDir);
end;

procedure RestoreEditableGlossary();
var
  TargetDir: String;
begin
  if GlossaryBackupDir = '' then
    exit;

  if not DirExists(GlossaryBackupDir) then
    exit;

  TargetDir := ExpandConstant('{app}\glossary');
  ForceDirectories(ExpandConstant('{app}'));

  if DirExists(TargetDir) then
    DelTree(TargetDir, True, True, True);

  if RenameFile(GlossaryBackupDir, TargetDir) then
    Log('Editable glossary folder was restored after uninstall: ' + TargetDir)
  else
    Log('Could not restore editable glossary folder after uninstall: ' + TargetDir);
end;

procedure InitializeWizard();
begin
  CleanupPage := CreateCustomPage(
    wpSelectDir,
    'Previous installation',
    'Choose how this installer should handle an already installed version.'
  );

  RemovePreviousCheckBox := TNewCheckBox.Create(CleanupPage);
  RemovePreviousCheckBox.Parent := CleanupPage.Surface;
  RemovePreviousCheckBox.Left := 0;
  RemovePreviousCheckBox.Top := ScaleY(12);
  RemovePreviousCheckBox.Width := CleanupPage.SurfaceWidth;
  RemovePreviousCheckBox.Height := ScaleY(20);
  RemovePreviousCheckBox.Caption := 'Remove previous installed version before installing';
  RemovePreviousCheckBox.Checked := True;

  RemovePreviousDescription := TNewStaticText.Create(CleanupPage);
  RemovePreviousDescription.Parent := CleanupPage.Surface;
  RemovePreviousDescription.Left := ScaleX(22);
  RemovePreviousDescription.Top := RemovePreviousCheckBox.Top + ScaleY(26);
  RemovePreviousDescription.Width := CleanupPage.SurfaceWidth - ScaleX(22);
  RemovePreviousDescription.Height := ScaleY(42);
  RemovePreviousDescription.Caption :=
    'Glossary folder is backed up before uninstall and restored after it.';
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Uninstaller: String;
  ResultCode: Integer;
begin
  if CurStep <> ssInstall then
    exit;

  if not RemovePreviousCheckBox.Checked then
    exit;

  if not GetPreviousUninstaller(Uninstaller) then
    exit;

  Log('Previous installation uninstall command found: ' + Uninstaller);
  BackupEditableGlossary();

  if not Exec(Uninstaller, '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '', SW_HIDE,
              ewWaitUntilTerminated, ResultCode) then
  begin
    RestoreEditableGlossary();
    MsgBox('Could not start previous version uninstaller. Installation will continue.',
           mbInformation, MB_OK);
    exit;
  end;

  RestoreEditableGlossary();

  if ResultCode <> 0 then
  begin
    MsgBox('Previous version uninstaller returned code ' + IntToStr(ResultCode) +
           '. Installation will continue.', mbInformation, MB_OK);
  end;
end;

[CustomMessages]
english.WelcomeLabel2=Install the SC2-style localization toolkit with bundled runtime and desktop shortcuts.
russian.WelcomeLabel2=Установка локализационного инструмента в стиле SC2 с комплектным runtime и ярлыками.
english.FinishedHeadingLabel=Localization Editor SC2 KSP is ready.
russian.FinishedHeadingLabel=Localization Editor SC2 KSP готов к работе.
