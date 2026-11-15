set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR AMD64)

# CC and CXX are supplied by build-muscriptor-helper-windows.sh as Zig
# compiler commands with the x86_64-windows-gnu target.  Keeping the target
# in those commands lets one toolchain file work with any absolute Zig path.
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
