Import-Module BitsTransfer
Install-Package -Force 7Zip4Powershell -ProviderName PowerShellGet
if ($Env:JDK -eq 9)
{
    $url = "https://download.java.net/java/GA/jdk9/9.0.4/binaries/openjdk-9.0.4_windows-x64_bin.tar.gz"
    $out = "C:\Program Files\Java\jdk9"
    Start-BitsTransfer -Source $url -Destination "$out.tar.gz"
    Expand-7Zip "$out.tar.gz" "$out"
    Expand-7Zip "$out\jdk9.tar" "$out"
    Move-Item "$out\jdk-9.0.4\*" "$out"
}
if ($Env:JDK -eq 10)
{
    $url = "https://download.java.net/java/GA/jdk10/10/binaries/openjdk-10_windows-x64_bin.tar.gz"
    $out = "C:\Program Files\Java\jdk10"
    Start-BitsTransfer -Source $url -Destination "$out.tar.gz"
    Expand-7Zip "$out.tar.gz" "$out"
    Expand-7Zip "$out\jdk10.tar" "$out"
    Move-Item "$out\jdk-10\*" "$out"
}