cmake_minimum_required(VERSION 3.20)
project(CaseZeroPortableTests CXX)
enable_testing()
find_package(Threads REQUIRED)
add_executable(cz_portable_tests native_tests.cpp ../gpu/bcn_decode.cpp
    ../android/touch_state.cpp ../host/stfs_extract.cpp)
target_compile_features(cz_portable_tests PRIVATE cxx_std_17)
target_link_libraries(cz_portable_tests PRIVATE Threads::Threads)
add_test(NAME portable-runtime COMMAND cz_portable_tests "${CMAKE_CURRENT_BINARY_DIR}/fixtures")
