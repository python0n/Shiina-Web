var inSearch = false;
var currentSearchQuery = '';
var currentPage = 1;
var searchAbortController = null;

function searchShiina(val, page = 1, append = false) {   
    let shiinaSearch = document.getElementById('shiina-search');
    if (val == null || val == '') {
        shiinaSearch.classList.add('d-none');
        currentSearchQuery = '';
        currentPage = 1;
        return;
    }

    if(!inSearch) {
        inSearch = true;
        currentSearchQuery = val;
        currentPage = page;
        
        // Cancel any previous request
        if (searchAbortController) {
            searchAbortController.abort();
        }
        searchAbortController = new AbortController();
        
        if(shiinaSearch.classList.contains('d-none')) {
            shiinaSearch.classList.remove('d-none');
        }
        
        fetch("/api/v1/search?query=" + encodeURIComponent(val) + "&page=" + page, {
            signal: searchAbortController.signal
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Search request failed');
            }
            return response.json();
        })
        .then(data => {
            // Only process results if this is still the current search query
            if (val === currentSearchQuery) {
                renderSearchResults(data, append);
                updateTooltips();
            }
        })
        .catch(error => {
            if (error.name !== 'AbortError') {
                console.error('Error:', error);
                if (!append && val === currentSearchQuery) {
                    shiinaSearch.innerHTML = '<div class="alert alert-danger m-2">' + tr('js.search_failed', 'Search failed. Please try again.') + '</div>';
                }
            }
        })
        .finally(() => {
            inSearch = false;
            searchAbortController = null;
        });
    }
}

function renderSearchResults(data, append = false) {
    const shiinaSearch = document.getElementById('shiina-search');
    
    if (!append) {
        shiinaSearch.innerHTML = "";
    }

    // Remove loading indicator if present
    const loadingIndicator = shiinaSearch.querySelector('.loading-indicator');
    if (loadingIndicator) {
        loadingIndicator.remove();
    }

    // Find or create results container
    let resultsContainer = shiinaSearch.querySelector('.list-group');
    if (!resultsContainer) {
        resultsContainer = document.createElement('div');
        resultsContainer.classList.add('list-group', 'list-group-flush');
        shiinaSearch.appendChild(resultsContainer);
    }

    // Render search results
    if (data.results && data.results.length > 0) {
        data.results.forEach((item) => {
            const resultItem = document.createElement('a');
            resultItem.href = getResultUrl(item);
            resultItem.classList.add('list-group-item', 'list-group-item-action', 'd-flex', 'align-items-center', 'py-3');

            // Avatar/Image
            if (item.source_table === 'maps') {
                const img = document.createElement('img');
                img.src = "https://assets.ppy.sh/beatmaps/" + item.set_id + "/covers/cover.jpg?1650681317";
                img.alt = item.item_name + ' Image';
                img.classList.add('me-3', 'rounded');
                img.style.width = '48px';
                img.style.height = '48px';
                img.style.objectFit = 'cover';
                resultItem.appendChild(img);
            } else if (item.source_table === 'users') {
                const img = document.createElement('img');
                img.src = avatarServer + "/" + item.id;
                img.alt = item.item_name + ' Avatar';
                img.classList.add('me-3', 'rounded');
                img.style.width = '48px';
                img.style.height = '48px';
                img.style.objectFit = 'cover';
                resultItem.appendChild(img);
            } else {
                // Default icon for other types
                const iconDiv = document.createElement('div');
                iconDiv.classList.add('me-3', 'd-flex', 'align-items-center', 'justify-content-center', 'bg-secondary', 'text-white', 'rounded');
                iconDiv.style.width = '48px';
                iconDiv.style.height = '48px';
                iconDiv.innerHTML = '<i class="fas fa-users"></i>';
                resultItem.appendChild(iconDiv);
            }

            // Content
            const contentDiv = document.createElement('div');
            contentDiv.classList.add('flex-grow-1');
            
            const title = document.createElement('h6');
            title.textContent = item.item_name;
            title.classList.add('mb-1', 'fw-semibold');

            const typeLabel = document.createElement('small');
            typeLabel.textContent = tr('js.search_type.' + item.source_table, capitalizeFirstLetter(item.source_table));
            typeLabel.classList.add('text-muted');

            contentDiv.appendChild(title);
            contentDiv.appendChild(typeLabel);

            resultItem.appendChild(contentDiv);
            
            // Add chevron icon
            const chevron = document.createElement('i');
            chevron.classList.add('fas', 'fa-chevron-right', 'text-muted');
            resultItem.appendChild(chevron);
            
            resultsContainer.appendChild(resultItem);
        });
    } else if (!append) {
        const noResults = document.createElement('div');
        noResults.classList.add('text-center', 'py-5', 'text-muted');
        noResults.innerHTML = '<i class="fas fa-search fa-2x mb-3"></i><br><h6>' + tr('js.no_results', 'No results found') + '</h6><small>' + tr('js.adjust_search', 'Try adjusting your search terms') + '</small>';
        resultsContainer.appendChild(noResults);
    }

    // Remove existing pagination
    const existingPagination = shiinaSearch.querySelector('.pagination-container');
    if (existingPagination) {
        existingPagination.remove();
    }

    // Add pagination controls if there are results
    if (data.results && data.results.length > 0) {
        const paginationContainer = createPaginationControls(data.page, data.has_next);
        shiinaSearch.appendChild(paginationContainer);
    }
}

function showLoadingIndicator() {
    const shiinaSearch = document.getElementById('shiina-search');
    
    // Remove existing loading indicator
    const existingLoading = shiinaSearch.querySelector('.loading-indicator');
    if (existingLoading) {
        existingLoading.remove();
    }
    
    const loadingDiv = document.createElement('div');
    loadingDiv.classList.add('loading-indicator', 'd-flex', 'align-items-center', 'justify-content-center', 'py-4');
    loadingDiv.innerHTML = `
        <div class="spinner-border spinner-border-sm me-2" role="status">
            <span class="visually-hidden">Loading...</span>
        </div>
        <small class="text-muted">${tr('js.searching', 'Searching...')}</small>
    `;
    
    shiinaSearch.appendChild(loadingDiv);
}

function createPaginationControls(currentPageNum, hasNext) {
    const paginationContainer = document.createElement('div');
    paginationContainer.classList.add('pagination-container', 'd-flex', 'justify-content-center', 'p-3', 'border-top');

    // Only show "Load More" button if there are more pages
    if (hasNext) {
        const loadMoreButton = document.createElement('button');
        loadMoreButton.classList.add('btn', 'btn-primary', 'btn-sm');
        loadMoreButton.innerHTML = '<i class="fas fa-plus me-2"></i>' + tr('js.load_more_results', 'Load More Results');
        
        loadMoreButton.onclick = () => {
            loadMoreButton.disabled = true;
            loadMoreButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>' + tr('js.loading', 'Loading...');
            searchShiina(currentSearchQuery, currentPageNum + 1, true);
        };

        paginationContainer.appendChild(loadMoreButton);
    } else {
        // Show end message
        const endMessage = document.createElement('small');
        endMessage.classList.add('text-muted', 'text-center');
        endMessage.innerHTML = '<i class="fas fa-check me-1"></i>' + tr('js.all_loaded', 'All results loaded');
        paginationContainer.appendChild(endMessage);
    }

    return paginationContainer;
}

function getResultUrl(item) {
    switch(item.source_table) {
        case 'users':
            return "/u/" + item.id;
        case 'maps':
            return "/b/" + item.id;
        case 'clans':
            return "/clan/" + item.id;
        default:
            return "#";
    }
}

function capitalizeFirstLetter(string) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}

// Helper function to handle keyboard navigation in search
function handleSearchKeydown(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        const searchInput = event.target;
        if (searchInput.value.trim()) {
            searchShiina(searchInput.value.trim(), 1, false);
        }
    } else if (event.key === 'Escape') {
        const shiinaSearch = document.getElementById('shiina-search');
        shiinaSearch.classList.add('d-none');
        currentSearchQuery = '';
        currentPage = 1;
    }
}

// Function to clear search results
function clearSearch() {
    const shiinaSearch = document.getElementById('shiina-search');
    shiinaSearch.classList.add('d-none');
    currentSearchQuery = '';
    currentPage = 1;
}

// Enhanced search with debouncing to prevent too many API calls
let searchTimeout;
function searchShiinaDebounced(val, delay = 300) {
    clearTimeout(searchTimeout);
    
    // Clear search if input is empty
    if (!val || val.trim() === '') {
        clearSearch();
        return;
    }
    
    // Only search if value has meaningful content (at least 2 characters)
    if (val.trim().length < 2) {
        return;
    }
    
    // Show loading indicator immediately for longer delays
    if (delay > 100) {
        const shiinaSearch = document.getElementById('shiina-search');
        if (shiinaSearch.classList.contains('d-none')) {
            shiinaSearch.classList.remove('d-none');
        }
        showLoadingIndicator();
    }
    
    searchTimeout = setTimeout(() => {
        // Always start from page 1 for new searches
        searchShiina(val.trim(), 1, false);
    }, delay);
}