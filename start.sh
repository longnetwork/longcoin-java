#!/bin/bash

# native *.so library of javaFX: 
# libavplugin.so libdecora_sse.so libfxplugins.so libglass.so libglassgtk2.so libglassgtk3.so libgstreamer-lite.so libjavafx_font.so libjavafx_font_freetype.so
# libjavafx_font_pango.so libjavafx_iio.so libjfxmedia.so libjfxwebkit.so libprism_common.so libprism_es2.so libprism_sw.so 

export PATH_TO_FX=/usr/share/openjfx/lib


java -Dfile.encoding=UTF-8 --module-path $PATH_TO_FX --add-modules=javafx.base,javafx.controls,javafx.web,javafx.media,javafx.graphics\
 -classpath bin Launcher




