# CMake package configuration file for SPIRV-Headers provided in FireLM
if(NOT TARGET SPIRV-Headers::SPIRV-Headers)
    add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED)
    set_target_properties(SPIRV-Headers::SPIRV-Headers PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/../../vulkan_include"
    )
endif()

if(NOT TARGET SPIRV-Headers)
    add_library(SPIRV-Headers INTERFACE IMPORTED)
    set_target_properties(SPIRV-Headers PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/../../vulkan_include"
    )
endif()

set(SPIRV-Headers_FOUND TRUE)
set(SPIRV_HEADERS_FOUND TRUE)
set(SPIRV-Headers_VERSION "1.5.5")
