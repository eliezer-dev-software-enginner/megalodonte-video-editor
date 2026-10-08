package megalodonte.cliente;

import megalodonte.script.ViewScript;

public final class Cadastro extends ViewScript {
    @Override public void onSubmit() {
        message("Cadastro válido. A criação da conta será integrada na próxima etapa.");
    }
}
