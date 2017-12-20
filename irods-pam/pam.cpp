#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <security/pam_appl.h>
#include <security/pam_modules.h>
#include <sys/wait.h>

PAM_EXTERN int 
pam_sm_authenticate(pam_handle_t *pamh, int options, int argc, 
        const char **argv) {
    int retval;
    const char* pUsername;
    const void *item;
    const struct pam_conv *conv;
    struct pam_response *resp;
    const struct pam_message *msgs[1];
    struct pam_message msg;

    retval = pam_get_user(pamh, &pUsername, "Username: ");
    if (retval != PAM_SUCCESS) return retval;

    retval = pam_get_item(pamh, PAM_CONV, &item);
    if (retval != PAM_SUCCESS) return retval;
    conv = (const struct pam_conv *) item;

    msg.msg_style = PAM_PROMPT_ECHO_OFF;
    msgs[0] = &msg;
    if ((retval = conv->conv(1, msgs, &resp, conv->appdata_ptr)) !=
            PAM_SUCCESS) {
        return retval;
    }

    // Open a pipe, make the read end the new stdin
    int py_comm[2];
    pipe(py_comm);
    close(STDIN_FILENO);
    dup2(py_comm[0], STDIN_FILENO);

    // Write username and JWT to write end of pipe
    write(py_comm[1], pUsername, strlen(pUsername));
    write(py_comm[1], "\n", 1);
    write(py_comm[1], resp[0].resp, strlen(resp[0].resp));
    close(py_comm[1]);

    // Call the check_jwt executable. When using `system` stdin is inherited,
    // thus it may read username and token from stdin.
    int result = system("check_jwt");

    // Release mememory and blank out token from memory
    memset(resp[0].resp, 0, strlen(resp[0].resp));
    free(resp[0].resp);
    free(resp);

    return WEXITSTATUS(result);
}
