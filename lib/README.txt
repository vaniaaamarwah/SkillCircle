SkillCircle - third-party library
=================================

File:     sqlite-jdbc-3.53.4.0.jar
Purpose:  the SQLite JDBC driver (lets Java talk to the SQLite database file)
Project:  https://github.com/xerial/sqlite-jdbc
License:  Apache License 2.0
SHA-256:  bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb
          (matches checksums_sha256.txt published with the 3.53.4.0 release)

Nothing else is needed: the only dependency this driver lists (slf4j) is optional.
The jar already contains the Windows native library.

To download it again (PowerShell, from the SkillCircle folder):

  Invoke-WebRequest -Uri "https://github.com/xerial/sqlite-jdbc/releases/download/3.53.4.0/sqlite-jdbc-3.53.4.0.jar" -OutFile "lib\sqlite-jdbc-3.53.4.0.jar"
  (Get-FileHash lib\sqlite-jdbc-3.53.4.0.jar -Algorithm SHA256).Hash

The printed hash must equal the SHA-256 above (letter case does not matter).
