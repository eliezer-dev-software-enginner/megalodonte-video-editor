package megalodonte.cliente;

import megalodonte.script.ViewScript;

public final class Login extends ViewScript {
    @Override public void onSubmit() {
        message("Formulário válido. Autenticação será integrada na próxima etapa.");
        set("senha", "");
    }
}
