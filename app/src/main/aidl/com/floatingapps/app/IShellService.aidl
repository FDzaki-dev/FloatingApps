// IShellService.aidl
package com.floatingapps.app;

interface IShellService {
    // Fixed transaction ID required by Shizuku's UserService protocol.
    void destroy() = 16777114;

    // Runs a shell command (argv array, no shell-string parsing) with
    // shell/root privileges and returns combined stdout+stderr.
    String execArr(in String[] command) = 1;
}
