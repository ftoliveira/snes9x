cmake_minimum_required(VERSION 3.22.1)
project("snes9x_android" CXX C)

# Root of the snes9x repository (5 levels up from this file)
set(SNES9X_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../..")

set(SNES9X_SOURCES
    ${SNES9X_ROOT}/apu/apu.cpp
    ${SNES9X_ROOT}/apu/bapu/dsp/sdsp.cpp
    ${SNES9X_ROOT}/apu/bapu/smp/smp.cpp
    ${SNES9X_ROOT}/apu/bapu/smp/smp_state.cpp
    ${SNES9X_ROOT}/bsx.cpp
    ${SNES9X_ROOT}/c4.cpp
    ${SNES9X_ROOT}/c4emu.cpp
    ${SNES9X_ROOT}/cheats.cpp
    ${SNES9X_ROOT}/cheats2.cpp
    ${SNES9X_ROOT}/clip.cpp
    ${SNES9X_ROOT}/conffile.cpp
    ${SNES9X_ROOT}/controls.cpp
    ${SNES9X_ROOT}/cpu.cpp
    ${SNES9X_ROOT}/cpuexec.cpp
    ${SNES9X_ROOT}/cpuops.cpp
    ${SNES9X_ROOT}/crosshairs.cpp
    ${SNES9X_ROOT}/dma.cpp
    ${SNES9X_ROOT}/dsp.cpp
    ${SNES9X_ROOT}/dsp1.cpp
    ${SNES9X_ROOT}/dsp2.cpp
    ${SNES9X_ROOT}/dsp3.cpp
    ${SNES9X_ROOT}/dsp4.cpp
    ${SNES9X_ROOT}/fxemu.cpp
    ${SNES9X_ROOT}/fxinst.cpp
    ${SNES9X_ROOT}/gfx.cpp
    ${SNES9X_ROOT}/globals.cpp
    ${SNES9X_ROOT}/loadzip.cpp
    ${SNES9X_ROOT}/memmap.cpp
    ${SNES9X_ROOT}/movie.cpp
    ${SNES9X_ROOT}/msu1.cpp
    ${SNES9X_ROOT}/obc1.cpp
    ${SNES9X_ROOT}/ppu.cpp
    ${SNES9X_ROOT}/sa1.cpp
    ${SNES9X_ROOT}/sa1cpu.cpp
    ${SNES9X_ROOT}/screenshot.cpp
    ${SNES9X_ROOT}/sdd1.cpp
    ${SNES9X_ROOT}/sdd1emu.cpp
    ${SNES9X_ROOT}/seta.cpp
    ${SNES9X_ROOT}/seta010.cpp
    ${SNES9X_ROOT}/seta011.cpp
    ${SNES9X_ROOT}/seta018.cpp
    ${SNES9X_ROOT}/sha256.cpp
    ${SNES9X_ROOT}/snapshot.cpp
    ${SNES9X_ROOT}/snes9x.cpp
    ${SNES9X_ROOT}/spc7110.cpp
    ${SNES9X_ROOT}/srtc.cpp
    ${SNES9X_ROOT}/stream.cpp
    ${SNES9X_ROOT}/tile.cpp
    ${SNES9X_ROOT}/tileimpl-h2x1.cpp
    ${SNES9X_ROOT}/tileimpl-n1x1.cpp
    ${SNES9X_ROOT}/tileimpl-n2x1.cpp
    ${SNES9X_ROOT}/bml.cpp
    ${SNES9X_ROOT}/fscompat.cpp
    ${SNES9X_ROOT}/filter/snes_ntsc.c
    ${SNES9X_ROOT}/unzip/unzip.c
    ${SNES9X_ROOT}/unzip/ioapi.c
    snes9x_android.cpp
)

add_library(snes9x_android SHARED ${SNES9X_SOURCES})

target_include_directories(snes9x_android PRIVATE
    ${SNES9X_ROOT}
    ${SNES9X_ROOT}/apu
    ${SNES9X_ROOT}/apu/bapu
)

target_compile_definitions(snes9x_android PRIVATE
    ANDROID
    HAVE_STRINGS_H
    HAVE_STDINT_H
    RIGHTSHIFT_IS_SAR
    UNZIP_SUPPORT
)

target_compile_options(snes9x_android PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-std=c++17>
    -O2
    -ffast-math
    -fno-strict-aliasing
)

target_link_libraries(snes9x_android
    android
    log
    GLESv2
)
