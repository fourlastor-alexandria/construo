package io.github.fourlastor.construo.editpe

import com.badlogic.gdx.utils.SharedLibraryLoader

object EditPE {
    external fun replaceIcon(exePath: String, icoPath: String, destinationPath: String)

    init {
        SharedLibraryLoader().load("editpe_java")
    }
}
