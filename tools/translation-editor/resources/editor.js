/* ------------------------------------------------------------------
 *  Live row filter
 * ------------------------------------------------------------------ */
document.getElementById('searchInput').addEventListener('input', ({target}) => {
  const needle = target.value.trim().toLowerCase();

  // find the <tbody> inside tableContainer (there is only one)
  const tbody = document.querySelector('#tableContainer tbody');
  if (!tbody) return;

  Array.from(tbody.rows).forEach(row => {
    const match = Array.from(row.cells).some(td =>
      td.textContent.toLowerCase().includes(needle)
    );
    row.style.display = match || needle === '' ? '' : 'none';
  });
});
